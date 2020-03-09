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

import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@Bean(name = "pendingSubscriptionModule", parent = PubSubComponent.class, active = true)
public class PendingSubscriptionModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.name("message")
			.add(ElementCriteria.name("x", new String[]{"xmlns", "type"}, new String[]{"jabber:x:data", "submit"}))
			.add(ElementCriteria.name("field", new String[]{"var"}, new String[]{"FORM_TYPE"}))
			.add(ElementCriteria.name("value", "http://jabber.org/protocol/pubsub#subscribe_authorization", null,
									  null));

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#get-pending"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet message) throws PubSubException {
		try {
			BareJID toJid = message.getStanzaTo().getBareJID();
			Element element = message.getElement();
			Form x = new Form(element.getChild("x", "jabber:x:data"));
			final String subId = x.getAsString("pubsub#subid");
			final String node = x.getAsString("pubsub#node");
			final BareJID subscriberJid = BareJID.bareJIDInstanceNS(x.getAsString("pubsub#subscriber_jid"));
			final Boolean allow = x.getAsBoolean("pubsub#allow");

			if (allow == null) {
				return;
			}

			JID jid = message.getStanzaFrom();

			pubSubLogic.checkPermission(toJid, node, jid, PubSubLogic.Action.manageNode);
			
			final ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, node);
			final IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(toJid, node);

			String userSubId = nodeSubscriptions.getSubscriptionId(subscriberJid);

			if ((subId != null) && !subId.equals(userSubId)) {
				throw new PubSubException(element, Authorization.NOT_ACCEPTABLE, PubSubErrorCondition.INVALID_SUBID);
			}

			Subscription subscription = nodeSubscriptions.getSubscription(subscriberJid);

			if (subscription != Subscription.pending) {
				return;
			}

			Affiliation affiliation = nodeAffiliations.getSubscriberAffiliation(jid.getBareJID()).getAffiliation();

			if (allow) {
				subscription = Subscription.subscribed;
				affiliation = Affiliation.member;
				nodeSubscriptions.changeSubscription(subscriberJid, subscription);
				nodeAffiliations.changeAffiliation(subscriberJid, affiliation);
			} else {
				subscription = Subscription.none;
				nodeSubscriptions.changeSubscription(subscriberJid, subscription);
			}
			this.getRepository().update(toJid, node, nodeSubscriptions);
			this.getRepository().update(toJid, node, nodeAffiliations);

			Packet msg = Message.getMessage(message.getStanzaTo(), JID.jidInstance(subscriberJid), null, null, null,
											null, Utils.createUID(subscriberJid));

			msg.getElement().addChild(SubscribeNodeModule.makeSubscription(node, subscriberJid, subscription, null));

			packetWriter.write(msg);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing pending subscription packet", e);
			throw new RuntimeException(e);
		}
	}

	public List<Packet> sendAuthorizationRequest(final String nodeName, final JID fromJid, final String subID,
												 final BareJID subscriberJid, IAffiliations nodeAffiliations)
			throws RepositoryException {
		Form x = new Form("form", "PubSub subscriber request",
						  "To approve this entity's subscription request, click the OK button. To deny the request, click the cancel button.");

		x.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#subscribe_authorization"));
		x.addField(Field.fieldHidden("pubsub#subid", subID));
		x.addField(Field.fieldTextSingle("pubsub#node", nodeName, "Node ID"));
		x.addField(
				Field.fieldJidSingle("pubsub#subscriber_jid", subscriberJid.toString(), "UsersSubscription Address"));
		x.addField(
				Field.fieldBoolean("pubsub#allow", Boolean.FALSE, "Allow this JID to subscribe to this pubsub node?"));

		List<Packet> result = new ArrayList<Packet>();
		UsersAffiliation[] affiliations = nodeAffiliations.getAffiliations();

		if (affiliations != null) {
			for (UsersAffiliation affiliation : affiliations) {
				if (affiliation.getAffiliation() == Affiliation.owner) {
					Packet message = Message.getMessage(fromJid, JID.jidInstance(affiliation.getJid()), null, null,
														null, null, Utils.createUID(affiliation.getJid()));

					message.getElement().addChild(x.getElement());
					result.add(message);
				}
			}
		}

		return result;
	}
}
