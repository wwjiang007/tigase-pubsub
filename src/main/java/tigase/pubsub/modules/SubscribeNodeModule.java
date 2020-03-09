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
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.utils.Logic;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@Bean(name = "subscribeNodeModule", parent = PubSubComponent.class, active = true)
public class SubscribeNodeModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT_SUBSCRIBE = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"))
			.add(ElementCriteria.name("subscribe"));
	@Inject
	private PendingSubscriptionModule pendingSubscriptionModule;
	@Inject
	private PublishItemModule publishItemModule;

	private static Affiliation calculateNewOwnerAffiliation(final Affiliation ownerAffiliation,
															final Affiliation newAffiliation) {
		if (ownerAffiliation.getWeight() > newAffiliation.getWeight()) {
			return ownerAffiliation;
		} else {
			return newAffiliation;
		}
	}

	public static Element makeSubscription(String nodeName, BareJID subscriberJid, Subscription newSubscription,
										   String subid) {
		Element resPubSub = new Element("pubsub", new String[]{"xmlns"},
										new String[]{"http://jabber.org/protocol/pubsub"});
		Element resSubscription = new Element("subscription");

		resPubSub.addChild(resSubscription);
		resSubscription.setAttribute("node", nodeName);
		resSubscription.setAttribute("jid", subscriberJid.toString());
		resSubscription.setAttribute("subscription", newSubscription.name());
		if (subid != null) {
			resSubscription.setAttribute("subid", subid);
		}

		return resPubSub;
	}

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#manage-subscriptions",
							"http://jabber.org/protocol/pubsub#auto-subscribe",
							"http://jabber.org/protocol/pubsub#subscribe",
							"http://jabber.org/protocol/pubsub#subscription-notifications"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_SUBSCRIBE;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		final BareJID serviceJid = packet.getStanzaTo().getBareJID();
		final Element pubSub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element subscribe = pubSub.getChild("subscribe");
		final JID senderJid = packet.getStanzaFrom();
		final String nodeName = subscribe.getAttributeStaticStr("node");
		final BareJID jid = BareJID.bareJIDInstanceNS(subscribe.getAttributeStaticStr("jid"));

		try {
			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);

			logic.checkRole(serviceJid, nodeName, senderJid, Logic.Action.subscribe);

			IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(serviceJid, nodeName);
			UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());

			if (!this.config.isAdmin(senderJid) && (senderAffiliation.getAffiliation() != Affiliation.owner) &&
					!jid.equals((senderJid.getBareJID()))) {
				throw new PubSubException(packet.getElement(), Authorization.BAD_REQUEST,
										  PubSubErrorCondition.INVALID_JID);
			}

			ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeName);

			// TODO 6.1.3.2 Presence Subscription Required
			// TODO 6.1.3.3 Not in Roster Group
			// TODO 6.1.3.4 Not on Whitelist
			// TODO 6.1.3.5 Payment Required
			// TODO 6.1.3.6 Anonymous NodeSubscriptions Not Allowed
			// TODO 6.1.3.9 NodeSubscriptions Not Supported
			// TODO 6.1.3.10 Node Has Moved
			
			AccessModel accessModel = nodeConfig.getNodeAccessModel();
			
			List<Packet> results = new ArrayList<Packet>();
			Subscription newSubscription = Subscription.none;
			Affiliation affiliation = nodeAffiliations.getSubscriberAffiliation(jid).getAffiliation();

			if (this.config.isAdmin(senderJid) || (senderAffiliation.getAffiliation() == Affiliation.owner)) {
				newSubscription = Subscription.subscribed;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
			} else {
				switch (accessModel) {
					case open:
					case presence:
					case roster:
					case whitelist:
						newSubscription = Subscription.subscribed;
						affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
						break;
					case authorize:
						newSubscription = Subscription.pending;
						affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.none);
						break;
				}
			}

			String subid = nodeSubscriptions.getSubscriptionId(jid);

			boolean sendLastPublishedItem = false;

			if (subid == null) {
				subid = nodeSubscriptions.addSubscriberJid(jid, newSubscription);
				nodeAffiliations.addAffiliation(jid, affiliation);
				if ((accessModel == AccessModel.authorize) && !(this.config.isAdmin(senderJid) ||
						(senderAffiliation.getAffiliation() == Affiliation.owner))) {
					results.addAll(
							this.pendingSubscriptionModule.sendAuthorizationRequest(nodeName, packet.getStanzaTo(),
																					subid, jid, nodeAffiliations));
				}
				sendLastPublishedItem = nodeConfig.getSendLastPublishedItem() == SendLastPublishedItem.on_sub ||
						nodeConfig.getSendLastPublishedItem() == SendLastPublishedItem.on_sub_and_presence;
			} else {
				nodeSubscriptions.changeSubscription(jid, newSubscription);
				nodeAffiliations.changeAffiliation(jid, affiliation);
			}

			// getRepository().setData(config.getServiceName(), nodeName,
			// "owner",
			// JIDUtils.getNodeID(element.getAttribute("from")));
			if (nodeSubscriptions.isChanged()) {
				this.getRepository().update(serviceJid, nodeName, nodeSubscriptions);
			}
			if (nodeAffiliations.isChanged()) {
				this.getRepository().update(serviceJid, nodeName, nodeAffiliations);
			}
			Packet result = packet.okResult(makeSubscription(nodeName, jid, newSubscription, subid), 0);

			results.add(result);

			packetWriter.write(results);

			if (sendLastPublishedItem) {
				publishItemModule.publishLastItem(serviceJid, nodeConfig, JID.jidInstance(jid));
			}
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing subscribe node packet", e);
			throw new RuntimeException(e);
		}
	}
}
