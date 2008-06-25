/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.modules;

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.PubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class NodeDeleteModule extends AbstractModule {

	private static final Criteria CRIT_DELETE = ElementCriteria.nameType("iq", "set").add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("delete"));
	private final PubSubRepository repository;

	public NodeDeleteModule(PubSubConfig config, PubSubRepository pubsubRepository) {
		this.repository = pubsubRepository;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#delete-nodes" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_DELETE;
	}

	public static Affiliation getUserAffiliation(final PubSubRepository repository, final String nodeName, final String jid)
			throws RepositoryException {
		Affiliation senderAffiliation = repository.getSubscriberAffiliation(nodeName, jid);
		if (senderAffiliation == null) {
			senderAffiliation = repository.getSubscriberAffiliation(nodeName, JIDUtils.getNodeID(jid));
		}
		return senderAffiliation;
	};

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
		final Element delete = pubSub.getChild("delete");
		final String nodeName = delete.getAttribute("node");
		try {
			if (nodeName == null) {
				throw new PubSubException(element, Authorization.NOT_ALLOWED);
			}
			NodeType nodeType = repository.getNodeType(nodeName);
			if (nodeType == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			String jid = element.getAttribute("from");
			Affiliation senderAffiliation = getUserAffiliation(this.repository, nodeName, jid);
			if (senderAffiliation != Affiliation.owner) {
				throw new PubSubException(element, Authorization.FORBIDDEN);
			}

			List<Element> resultArray = makeArray(createResultIQ(element));
			LeafNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
			if (nodeConfig.isNotify_config()) {
				String pssJid = element.getAttribute("to");
				String[] jids = repository.getSubscribersJid(nodeName);
				for (String sjid : jids) {
					Affiliation affiliation = NodeDeleteModule.getUserAffiliation(this.repository, nodeName, sjid);
					if (affiliation == null || affiliation == Affiliation.none || affiliation == Affiliation.outcast)
						continue;
					Element message = new Element("message", new String[] { "from", "to" }, new String[] { pssJid, sjid });
					Element event = new Element("event", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#event" });
					Element configuration = new Element("delete", new String[] { "node" }, new String[] { nodeName });
					event.addChild(configuration);
					message.addChild(event);

					resultArray.add(message);
				}
			}

			log.fine("Delete node [" + nodeName + "]");
			repository.deleteNode(nodeName);
			return resultArray;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

}