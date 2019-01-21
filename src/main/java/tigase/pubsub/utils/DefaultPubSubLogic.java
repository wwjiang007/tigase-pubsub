/**
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
package tigase.pubsub.utils;

import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Map;

import static tigase.pubsub.modules.PublishItemModule.AMP_XMLNS;

/**
 * Helper bean containing PubSub logic
 * <p>
 * Created by andrzej on 25.12.2016.
 */
@Bean(name = "logic", parent = PubSubComponent.class, active = true)
public class DefaultPubSubLogic
		implements Logic {

	@Inject
	private PubSubConfig pubSubConfig;

	@Inject
	private IPubSubRepository repository;

	@Override
	public void checkAccessPermission(BareJID serviceJid, String nodeName, JID senderJid)
			throws PubSubException, RepositoryException {
		if (pubSubConfig.isAdmin(senderJid)) {
			return;
		}

		checkAccessPermission(serviceJid, repository.getNodeConfig(serviceJid, nodeName),
							  repository.getNodeAffiliations(serviceJid, nodeName),
							  repository.getNodeSubscriptions(serviceJid, nodeName), senderJid);
	}

	@Override
	public void checkAccessPermission(BareJID serviceJid, AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations,
									  ISubscriptions nodeSubscriptions, JID senderJid)
			throws PubSubException, RepositoryException {
		//AbstractNodeConfig nodeConfig = node.getNodeConfig();
		if (nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND);
		}
		if ((nodeConfig.getNodeAccessModel() == AccessModel.open) &&
				!Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains())) {
			throw new PubSubException(Authorization.FORBIDDEN);
		}

		//IAffiliations nodeAffiliations = node.getNodeAffiliations();
		UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());

		if (senderAffiliation.getAffiliation() == Affiliation.outcast) {
			throw new PubSubException(Authorization.FORBIDDEN);
		}

		//ISubscriptions nodeSubscriptions = node.getNodeSubscriptions();
		Subscription senderSubscription = nodeSubscriptions.getSubscription(senderJid.getBareJID());

		if ((nodeConfig.getNodeAccessModel() == AccessModel.whitelist) &&
				!senderAffiliation.getAffiliation().isRetrieveItem()) {
			throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
		} else if ((nodeConfig.getNodeAccessModel() == AccessModel.authorize) &&
				((senderSubscription != Subscription.subscribed) ||
						!senderAffiliation.getAffiliation().isRetrieveItem())) {
			throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_SUBSCRIBED);
		} else if (nodeConfig.getNodeAccessModel() == AccessModel.presence) {
			boolean allowed = hasSenderSubscription(senderJid.getBareJID(), nodeAffiliations, nodeSubscriptions);

			if (!allowed) {
				throw new PubSubException(Authorization.NOT_AUTHORIZED,
										  PubSubErrorCondition.PRESENCE_SUBSCRIPTION_REQUIRED);
			}
		} else if (nodeConfig.getNodeAccessModel() == AccessModel.roster) {
			boolean allowed = isSenderInRosterGroup(senderJid.getBareJID(), nodeConfig, nodeAffiliations,
													nodeSubscriptions);

			if (!allowed) {
				throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_IN_ROSTER_GROUP);
			}
		}
	}

	@Override
	public Element prepareNotificationMessage(JID from, JID to, String id, Element itemToSend,
											  Map<String, String> headers) {
		Element message = new Element("message", new String[]{"xmlns", "from", "to", "id"},
									  new String[]{Packet.CLIENT_XMLNS, from.toString(), to.toString(), id});
		Element event = new Element("event", new String[]{"xmlns"},
									new String[]{"http://jabber.org/protocol/pubsub#event"});

		event.addChild(itemToSend);
		String expireAttr = itemToSend.getAttributeStaticStr(new String[]{"items", "item"}, "expire-at");
		if (expireAttr != null) {
			Element amp = new Element("amp");
			amp.setXMLNS(AMP_XMLNS);
			amp.addChild(new Element("rule", new String[]{"condition", "action", "value"},
									 new String[]{"expire-at", "drop", expireAttr}));
			message.addChild(amp);
		}
		message.addChild(event);
		if ((headers != null) && (headers.size() > 0)) {
			Element headElem = new Element("headers", new String[]{"xmlns"},
										   new String[]{"http://jabber.org/protocol/shim"});

			for (Map.Entry<String, String> entry : headers.entrySet()) {
				Element h = new Element("header", entry.getValue(), new String[]{"name"}, new String[]{entry.getKey()});

				headElem.addChild(h);
			}
			message.addChild(headElem);
		}

		return message;
	}

	public boolean hasSenderSubscription(final BareJID bareJid, final IAffiliations affiliations,
										 final ISubscriptions subscriptions) throws RepositoryException {

		for (UsersAffiliation affiliation : affiliations.getAffiliations()) {
			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}

			if (bareJid.equals(affiliation.getJid())) {
				return true;
			}

			Map<BareJID, RosterElement> buddies = repository.getUserRoster(affiliation.getJid());
			RosterElement re = buddies.get(bareJid);
			if (re != null) {
				if (re.getSubscription() == RosterAbstract.SubscriptionType.both ||
						re.getSubscription() == RosterAbstract.SubscriptionType.from ||
						re.getSubscription() == RosterAbstract.SubscriptionType.from_pending_out) {
					return true;
				}
			}
		}

		return false;
	}

	public boolean isSenderInRosterGroup(BareJID bareJid, AbstractNodeConfig nodeConfig, IAffiliations affiliations,
										 final ISubscriptions subscriptions) throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		final String[] groupsAllowed = nodeConfig.getRosterGroupsAllowed();

		if ((groupsAllowed == null) || (groupsAllowed.length == 0)) {
			return true;
		}

		// @TODO - is there a way to optimize this?
		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(owner.getJid());

			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}
			if (bareJid.equals(owner)) {
				return true;
			}

			Map<BareJID, RosterElement> buddies = repository.getUserRoster(owner.getJid());
			RosterElement re = buddies.get(bareJid);
			if (re != null && re.getGroups() != null) {
				for (String group : groupsAllowed) {
					if (Utils.contain(group, groupsAllowed)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	protected PubSubConfig getPubSubConfig() {
		return pubSubConfig;
	}

	protected IPubSubRepository getRepository() {
		return repository;
	}
}
