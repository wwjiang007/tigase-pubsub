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

import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of subscription management module for PubSub component.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:25:49 GMT
 */
@Bean(name = "manageSubscriptionModule", parent = PubSubComponent.class, active = true)
public class ManageSubscriptionModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.name("iq")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner"))
			.add(ElementCriteria.name("subscriptions"));
	private Logger log = Logger.getLogger(this.getClass().getName());

	private static Packet createSubscriptionNotification(JID fromJid, JID toJid, String nodeName,
														 Subscription subscription) {
		Packet message = Message.getMessage(fromJid, toJid, null, null, null, null, null);
		Element pubsub = new Element("pubsub", new String[]{"xmlns"},
									 new String[]{"http://jabber.org/protocol/pubsub"});

		message.getElement().addChild(pubsub);

		Element affilations = new Element("subscriptions", new String[]{"node"}, new String[]{nodeName});

		pubsub.addChild(affilations);
		affilations.addChild(new Element("subscription", new String[]{"jid", "subscription"},
										 new String[]{toJid.toString(), subscription.name()}));

		return message;
	}

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#manage-subscriptions"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			BareJID toJid = packet.getStanzaTo().getBareJID();
			Element element = packet.getElement();
			Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			Element subscriptions = pubsub.getChild("subscriptions");
			String nodeName = subscriptions.getAttributeStaticStr("node");
			StanzaType type = packet.getType();

			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}

			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}

			ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, nodeName);
			IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(toJid, nodeName);
			JID senderJid = packet.getStanzaFrom();

			checkPrivileges(type, element, senderJid, nodeConfig, nodeAffiliations, nodeSubscriptions);

			if (type == StanzaType.get) {
				processGet(packet, subscriptions, nodeName, nodeSubscriptions, packetWriter);
			} else if (type == StanzaType.set) {
				processSet(packet, subscriptions, nodeName, nodeConfig, nodeSubscriptions, packetWriter);
			} else {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}

		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing subscription packet", e);

			throw new RuntimeException(e);
		}
	}

	private void checkPrivileges(StanzaType type, Element element, JID senderJid, AbstractNodeConfig nodeConfig,
								 IAffiliations nodeAffiliations, ISubscriptions nodeSubscriptions)
			throws PubSubException {
		boolean allowed = false;

		// for "tigase:pubsub:1"
		if (!allowed && type == StanzaType.get && nodeConfig.isAllowToViewSubscribers()) {
			Subscription senderSubscription = nodeSubscriptions.getSubscription(senderJid.getBareJID());
			allowed = senderSubscription == Subscription.subscribed;
		}

		if (!allowed) {
			UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());
			allowed = senderAffiliation.getAffiliation() == Affiliation.owner;
		}

		if (!allowed && this.config.isAdmin(senderJid)) {
			allowed = true;
		}

		if (!allowed) {
			throw new PubSubException(element, Authorization.FORBIDDEN);
		}
	}

	private void processGet(Packet packet, Element subscriptions, String nodeName,
							final ISubscriptions nodeSubscriptions, PacketWriter packetWriter)
			throws RepositoryException, PubSubException {
		Element ps = new Element("pubsub", new String[]{"xmlns"},
								 new String[]{"http://jabber.org/protocol/pubsub#owner"});

		Packet iq = packet.okResult(ps, 0);

		Element afr = new Element("subscriptions", new String[]{"node"}, new String[]{nodeName});

		SubscriptionFilter subscriptionFilter = null;
		Element filterE = subscriptions.getChild("filter", PresencePerNodeExtension.XMLNS_EXTENSION);
		if (filterE != null) {
			subscriptionFilter = new SubscriptionFilter(filterE);
		}

		ps.addChild(afr);

		UsersSubscription[] subscribers = nodeSubscriptions.getSubscriptions();

		if (log.isLoggable(Level.FINEST)) {
			log.finest("Node subscriptions: " + nodeName + " / " + Arrays.toString(subscribers));
		}

		if (subscribers != null) {
			for (UsersSubscription usersSubscription : subscribers) {
				if (usersSubscription.getSubscription() == Subscription.none) {
					continue;
				}

				if (subscriptionFilter != null && !subscriptionFilter.match(usersSubscription)) {
					continue;
				}

				Element subscription = new Element("subscription", new String[]{"jid", "subscription"},
												   new String[]{usersSubscription.getJid().toString(),
																usersSubscription.getSubscription().name()});

				afr.addChild(subscription);
			}
		}

		if (nodeSubscriptions.isChanged()) {
			getRepository().update(packet.getStanzaTo().getBareJID(), nodeName, nodeSubscriptions);
		}

		packetWriter.write(iq);
	}

	private void processSet(Packet packet, Element subscriptions, String nodeName, final AbstractNodeConfig nodeConfig,
							final ISubscriptions nodeSubscriptions, PacketWriter packetWriter)
			throws PubSubException, RepositoryException {
		List<Element> subss = subscriptions.getChildren();

		for (Element a : subss) {
			if (!"subscription".equals(a.getName())) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
		}

		Map<JID, Subscription> changedSubscriptions = new HashMap<JID, Subscription>();

		for (Element af : subss) {
			String strSubscription = af.getAttributeStaticStr("subscription");
			String jidStr = af.getAttributeStaticStr("jid");
			JID jid = JID.jidInstanceNS(jidStr);

			if (strSubscription == null) {
				continue;
			}

			Subscription newSubscription = Subscription.valueOf(strSubscription);
			Subscription oldSubscription = nodeSubscriptions.getSubscription(jid.getBareJID());

			oldSubscription = (oldSubscription == null) ? Subscription.none : oldSubscription;
			if ((oldSubscription == Subscription.none) && (newSubscription != Subscription.none)) {
				nodeSubscriptions.addSubscriberJid(jid.getBareJID(), newSubscription);
				changedSubscriptions.put(jid, newSubscription);
			} else {
				nodeSubscriptions.changeSubscription(jid.getBareJID(), newSubscription);
				changedSubscriptions.put(jid, newSubscription);
			}
		}

		if (nodeSubscriptions.isChanged()) {
			getRepository().update(packet.getStanzaTo().getBareJID(), nodeName, nodeSubscriptions);
		}

		for (Map.Entry<JID, Subscription> entry : changedSubscriptions.entrySet()) {
			if (nodeConfig.isTigaseNotifyChangeSubscriptionAffiliationState()) {
				packetWriter.write(createSubscriptionNotification(packet.getStanzaTo(), entry.getKey(), nodeName,
																  entry.getValue()));
			}
		}

		Packet iq = packet.okResult((Element) null, 0);
		packetWriter.write(iq);
	}

	private class SubscriptionFilter {

		private String jidContains;

		public SubscriptionFilter(Element f) throws PubSubException {
			for (Element e : f.getChildren()) {
				if ("jid".equals(e.getName())) {
					this.jidContains = e.getAttributeStaticStr("contains");
				} else {
					throw new PubSubException(Authorization.BAD_REQUEST, "Unknown filter '" + e.getName() + "'");
				}
			}
		}

		public boolean match(UsersSubscription usersSubscription) {
			boolean match = true;

			if (jidContains != null) {
				match = match && usersSubscription.getJid().toString().contains(jidContains);
			}

			return match;
		}

	}
}
