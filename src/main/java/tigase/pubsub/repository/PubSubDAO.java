/*
 * PubSubDAO.java
 *
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Form;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.impl.roster.RosterFlat;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.mam.Query;
import tigase.xmpp.rsm.RSM;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author andrzej
 */
public abstract class PubSubDAO<T, S extends DataSource, Q extends tigase.pubsub.modules.mam.Query>
		implements IPubSubDAO<T, S, Q> {

	protected static final Logger log = Logger.getLogger(PubSubDAO.class.getCanonicalName());

	private final SimpleParser parser = SingletonFactory.getParserInstance();
	@Inject
	private UserRepository repository;

	protected static <Q extends Query> void calculateOffsetAndPosition(Q query, int count, Integer before,
																	   Integer after) {
		RSM rsm = query.getRsm();
		int index = rsm.getIndex() == null ? 0 : rsm.getIndex();
		int limit = rsm.getMax();

		if (after != null) {
			// it is ok, if we go out of range we will return empty result
			index = after + 1;
		} else if (before != null) {
			index = before - rsm.getMax();
			// if we go out of range we need to set index to 0 and reduce limit
			// to return proper results
			if (index < 0) {
				index = 0;
				limit = before;
			}
		} else if (rsm.hasBefore()) {
			index = count - rsm.getMax();
			if (index < 0) {
				index = 0;
			}
		}
		rsm.setIndex(index);
		rsm.setMax(limit);
		rsm.setCount(count);
	}

	protected PubSubDAO() {
	}

	@Override
	public void destroy() {

	}

	@Override
	public String[] getBuddyGroups(BareJID owner, BareJID buddy) throws RepositoryException {
		try {
			return this.repository.getDataList(owner, "roster/" + buddy, "groups");
		} catch (Exception e) {
			throw new RepositoryException("Getting buddy groups error", e);
		}
	}

	@Override
	public String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException {
		try {
			return this.repository.getData(owner, "roster/" + buddy, "subscription");
		} catch (Exception e) {
			throw new RepositoryException("Getting buddy subscription status error", e);
		}
	}

	@Override
	public Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException {
		try {
			String tmp = this.repository.getData(owner, "roster");
			Map<BareJID, RosterElement> roster = new HashMap<BareJID, RosterElement>();
			if (tmp != null) {
				RosterFlat.parseRosterUtil(tmp, roster, null);
			}
			return roster;
		} catch (UserNotFoundException ex) {
			if (log.isLoggable(Level.FINER)) {
				log.log(Level.FINER, "Cannot find roster of user {0}. Probably anonymous user.", new Object[]{owner});
			}
			return Collections.emptyMap();
		} catch (Exception e) {
			throw new RepositoryException("Getting user roster error", e);
		}
	}

	@Override
	public AbstractNodeConfig parseConfig(String nodeName, String data) throws RepositoryException {

		try {
			Form cnfForm = parseConfigForm(data);

			if (cnfForm == null) {
				return null;
			}

			NodeType type = NodeType.valueOf(cnfForm.getAsString("pubsub#node_type"));
			Class<? extends AbstractNodeConfig> cl = null;

			switch (type) {
				case collection:
					cl = CollectionNodeConfig.class;
					break;
				case leaf:
					cl = LeafNodeConfig.class;
					break;
				default:
					throw new RepositoryException("Unknown node type " + type);
			}

			AbstractNodeConfig nc = getNodeConfig(cl, nodeName, cnfForm);
			return nc;
		} catch (RepositoryException e) {
			throw e;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}

	protected <T extends AbstractNodeConfig> T getNodeConfig(final Class<T> nodeConfigClass, final String nodeName,
															 final Form configForm) throws RepositoryException {
		try {
			Constructor<T> constructor = nodeConfigClass.getConstructor(String.class);
			T nodeConfig = constructor.newInstance(nodeName);

			nodeConfig.copyFromForm(configForm);

			return nodeConfig;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}

	protected Element itemDataToElement(String data) {
		return itemDataToElement(data.toCharArray());
	}

	protected Element itemDataToElement(char[] data) {
		DomBuilderHandler domHandler = new DomBuilderHandler();
		parser.parse(domHandler, data, 0, data.length);
		Queue<Element> q = domHandler.getParsedElements();

		return q.element();
	}

	protected Form parseConfigForm(String cnfData) {
		if (cnfData == null) {
			return null;
		}

		char[] data = cnfData.toCharArray();
		DomBuilderHandler domHandler = new DomBuilderHandler();
		parser.parse(domHandler, data, 0, data.length);

		Queue<Element> q = domHandler.getParsedElements();
		if ((q != null) && (q.size() > 0)) {
			Form form = new Form(q.element());
			return form;
		}

		return null;
	}

	public static class Item<T>
			implements IPubSubRepository.Item {

		private final String itemId;
		private final T nodeId;
		private final String nodeName;
		private final Date ts;
		private Element item;

		public Item(String nodeName, T nodeId, String itemId, Date ts, Element item) {
			this.nodeName = nodeName;
			this.nodeId = nodeId;
			this.itemId = itemId;
			this.ts = ts;
			this.item = item;
		}

		@Override
		public String getId() {
			return nodeId.toString() + "," + itemId;
		}

		@Override
		public String getItemId() {
			return itemId;
		}

		@Override
		public Element getMessage() {
			return item;
		}

		@Override
		public void setMessage(Element item) {
			this.item = item;
		}

		@Override
		public Date getTimestamp() {
			return ts;
		}

		@Override
		public String getNode() {
			return nodeName;
		}
	}

}
