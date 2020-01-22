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
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;

import java.util.Map;
import java.util.logging.Level;

/**
 * Implementation of subscription retrieval module.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:27:10 GMT
 */
@Bean(name = "retrieveSubscriptionsModule", parent = PubSubComponent.class, active = true)
public class RetrieveSubscriptionsModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"))
			.add(ElementCriteria.name("subscriptions"));

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#retrieve-subscriptions"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			final BareJID serviceJid = packet.getStanzaTo().getBareJID();
			final Element pubsub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element subscriptions = pubsub.getChild("subscriptions");
			final String nodeName = subscriptions.getAttributeStaticStr("node");
			final String senderJid = packet.getStanzaFrom().toString();
			final BareJID senderBareJid = packet.getStanzaFrom().getBareJID();
			final Element pubsubResult = new Element("pubsub", new String[]{"xmlns"},
													 new String[]{"http://jabber.org/protocol/pubsub"});

			final Element subscriptionsResult = new Element("subscriptions");

			pubsubResult.addChild(subscriptionsResult);
			if (nodeName == null) {
				IPubSubDAO directRepo = this.getRepository().getPubSubDAO();
				Map<String, UsersSubscription> usersSubscriptions = directRepo.getUserSubscriptions(serviceJid,
																									senderBareJid);
				for (Map.Entry<String, UsersSubscription> entry : usersSubscriptions.entrySet()) {
					UsersSubscription subscription = entry.getValue();
					Element a = new Element("subscription", new String[]{"node", "jid", "subscription"},
											new String[]{entry.getKey(), senderBareJid.toString(),
														 subscription.getSubscription().name()});

					subscriptionsResult.addChild(a);
				}
			} else {
				AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);

				if (nodeConfig == null) {
					throw new PubSubException(packet.getElement(), Authorization.ITEM_NOT_FOUND);
				}

				ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeName);

				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST,
							"Getting node subscription, serviceJid: {0}, nodeName: {1}, nodeConfig: {2}, nodeSubscriptions: {3}",
							new Object[]{serviceJid, nodeName, nodeConfig, nodeSubscriptions});
				}
				subscriptionsResult.addAttribute("node", nodeName);

				nodeSubscriptions.getSubscriptions()
						.map(usersSubscription -> new Element("subscription",
															  new String[]{"jid", "subscription", "subid"},
															  new String[]{usersSubscription.getJid().toString(),
																		   usersSubscription.getSubscription().name(),
																		   usersSubscription.getSubid()}))
						.forEach(subscriptionsResult::addChild);
			}

			Packet result = packet.okResult(pubsubResult, 0);
			packetWriter.write(result);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing retrieve subscriptions packet", e);

			throw new RuntimeException(e);
		}
	}
}
