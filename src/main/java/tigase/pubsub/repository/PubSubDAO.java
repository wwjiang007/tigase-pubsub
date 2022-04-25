/*
 * Tigase PubSub - Publish Subscribe component for Tigase
 * Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
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
import tigase.xmpp.mam.util.MAMUtil;
import tigase.xmpp.mam.util.Range;
import tigase.xmpp.rsm.RSM;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author andrzej
 */
public abstract class PubSubDAO<T, S extends DataSource, Q extends tigase.pubsub.modules.mam.PubSubQuery>
		implements IPubSubDAO<T, S, Q> {

	protected static final Logger log = Logger.getLogger(PubSubDAO.class.getCanonicalName());

	private final SimpleParser parser = SingletonFactory.getParserInstance();
	@Inject
	private UserRepository repository;

	protected static void calculateOffsetAndPosition(RSM rsm, int count, Integer before,
																	   Integer after) {
		MAMUtil.calculateOffsetAndPosition(rsm, count, before, after, Range.FULL);
	}

	protected PubSubDAO() {
	}

	@Override
	public void destroy() {

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
		if (data == null) {
			return null;
		}
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

	public static class MAMItem implements IPubSubRepository.Item {

		private final String itemUuid;
		private final Date ts;
		private final Element message;

		public MAMItem(String itemUuid, Date ts, Element message) {
			this.itemUuid = itemUuid.toLowerCase();
			this.ts = ts;
			this.message = message;
		}

		@Override
		public String getId() {
			return itemUuid;
		}

		@Override
		public Element getMessage() {
			return message;
		}

		@Override
		public Date getTimestamp() {
			return ts;
		}

	}
	
}
