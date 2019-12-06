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
package tigase.pubsub.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.logging.Level;

@Bean(name = "nodeDeleteModule", parent = PubSubComponent.class, active = true)
public class NodeDeleteModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT_DELETE = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner"))
			.add(ElementCriteria.name("delete"));
	@Inject
	private EventBus eventBus;
	@Inject
	private PublishItemModule publishModule;

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#delete-nodes"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_DELETE;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
		final Element delete = pubSub.getChild("delete");
		final String nodeName = delete.getAttributeStaticStr("node");

		try {
			if (nodeName == null) {
				throw new PubSubException(element, Authorization.NOT_ALLOWED);
			}

			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			final IAffiliations nodeAffiliations = this.getRepository().getNodeAffiliations(toJid, nodeName);
			JID jid = packet.getStanzaFrom();

			if (!this.config.isAdmin(jid)) {
				UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(jid.getBareJID());

				if (!senderAffiliation.getAffiliation().isDeleteNode()) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}

			Packet result = packet.okResult((Element) null, 0);

			if (nodeConfig.isNotify_config()) {
				ISubscriptions nodeSubscriptions = this.getRepository().getNodeSubscriptions(toJid, nodeName);
				Element del = new Element("delete", new String[]{"node"}, new String[]{nodeName});

				this.publishModule.sendNotifications(del, packet.getStanzaTo(), nodeName, nodeConfig, nodeAffiliations,
													 nodeSubscriptions);
			}

			final String parentNodeName = nodeConfig.getCollection();

			if (parentNodeName == null || "".equals(parentNodeName)) {
				getRepository().removeFromRootCollection(toJid, nodeName);
			}
			if (nodeConfig instanceof CollectionNodeConfig) {
				CollectionNodeConfig cnc = (CollectionNodeConfig) nodeConfig;
				final String[] childrenNodes = getRepository().getChildNodes(toJid, nodeName);

				if ((childrenNodes != null) && (childrenNodes.length > 0)) {
					for (String childNodeName : childrenNodes) {
						AbstractNodeConfig childNodeConfig = getRepository().getNodeConfig(toJid, childNodeName);

						if (childNodeConfig != null) {
							childNodeConfig.setCollection(parentNodeName);
							getRepository().update(toJid, childNodeName, childNodeConfig);
						}
						if (parentNodeName == null || "".equals(parentNodeName)) {
							getRepository().addToRootCollection(toJid, childNodeName);
						}
					}
				}
			}
			log.fine("Delete node [" + nodeName + "]");
			getRepository().deleteNode(toJid, nodeName);

			eventBus.fire(new NodeDeletedEvent(toJid, nodeName));

			packetWriter.write(result);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing node delete packet", e);

			throw new RuntimeException(e);
		}
	}

	public static class NodeDeletedEvent {

		public final String node;
		public final BareJID serviceJid;

		public NodeDeletedEvent(BareJID serviceJid, String node) {
			this.serviceJid = serviceJid;
			this.node = node;
		}

	}

}
