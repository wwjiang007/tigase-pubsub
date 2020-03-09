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
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.logging.Level;

@Bean(name = "unsubscribeNodeModule", parent = PubSubComponent.class, active = true)
public class UnsubscribeNodeModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT_UNSUBSCRIBE = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"))
			.add(ElementCriteria.name("unsubscribe"));

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_UNSUBSCRIBE;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element unsubscribe = pubSub.getChild("unsubscribe");
		final JID senderJid = packet.getStanzaFrom();
		final String nodeName = unsubscribe.getAttributeStaticStr("node");
		final BareJID jid = BareJID.bareJIDInstanceNS(unsubscribe.getAttributeStaticStr("jid"));
		final String subid = unsubscribe.getAttributeStaticStr("subid");

		try {
			if (!senderJid.getBareJID().equals(jid)) {
				pubSubLogic.checkPermission(toJid, nodeName, senderJid, PubSubLogic.Action.manageNode);
			}
			
			ISubscriptions nodeSubscriptions = this.getRepository().getNodeSubscriptions(toJid, nodeName);

			if (subid != null) {
				String s = nodeSubscriptions.getSubscriptionId(jid);

				if (!subid.equals(s)) {
					throw new PubSubException(element, Authorization.NOT_ACCEPTABLE,
											  PubSubErrorCondition.INVALID_SUBID);
				}
			}

			Subscription subscription = nodeSubscriptions.getSubscription(jid);

			if (subscription == null) {
				throw new PubSubException(Authorization.UNEXPECTED_REQUEST, PubSubErrorCondition.NOT_SUBSCRIBED);
			}
			nodeSubscriptions.changeSubscription(jid, Subscription.none);
			this.getRepository().update(toJid, nodeName, nodeSubscriptions);

			packetWriter.write(packet.okResult((Element) null, 0));
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing unsubscribe node packet", e);
			throw new RuntimeException(e);
		}
	}
}
