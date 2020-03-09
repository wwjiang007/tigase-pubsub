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
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;

import java.util.Collections;
import java.util.logging.Level;

@Bean(name = "purgeItemsModule", parent = PubSubComponent.class, active = true)
public class PurgeItemsModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner"))
			.add(ElementCriteria.name("purge"));

	@Inject
	private PublishItemModule publishModule;

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#purge-nodes"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element pubSub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
		final Element purge = pubSub.getChild("purge");
		final String nodeName = purge.getAttributeStaticStr("node");

		try {
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}

			pubSubLogic.checkPermission(toJid, nodeName, packet.getStanzaTo(), PubSubLogic.Action.purgeNode);

			AbstractNodeConfig nodeConfig = this.getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(packet.getElement(), Authorization.ITEM_NOT_FOUND);
			} else if (nodeConfig.getNodeType() == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED,
										  new PubSubErrorCondition("unsupported", "purge-nodes"));
			}

			LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;

			if (!leafNodeConfig.isPersistItem()) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED,
										  new PubSubErrorCondition("unsupported", "persistent-items"));
			}

			Packet result = packet.okResult((Element) null, 0);

			final IItems nodeItems = this.getRepository().getNodeItems(toJid, nodeName);
			String[] itemsToDelete = nodeItems.getItemsIds(CollectionItemsOrdering.byUpdateDate);

			publishModule.generateNotifications(packet.getStanzaTo().getBareJID(), nodeName, Collections
					.singletonList(new Element("purge", new String[]{"node"}, new String[]{nodeName})), null, false);

			log.info("Purging node " + nodeName);
			if (itemsToDelete != null) {
				for (String id : itemsToDelete) {
					nodeItems.deleteItem(id);
				}
			}

			packetWriter.write(result);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing purge items packet", e);

			throw new RuntimeException(e);
		}
	}
}
