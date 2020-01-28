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
package tigase.pubsub.utils;

import tigase.component.exceptions.RepositoryException;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.PresenceCollectorModule;
import tigase.pubsub.modules.XsltTool;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static tigase.pubsub.modules.PublishItemModule.AMP_XMLNS;

/**
 * Helper bean containing PubSub logic
 * <p>
 * Created by andrzej on 25.12.2016.
 */
@Bean(name = "logic", parent = PubSubComponent.class, active = true)
public class DefaultPubSubLogic
		implements PubSubLogic {

	@Inject
	private EventBus eventBus;

	@Inject(bean = "service")
	private PubSubComponent component;

	@Inject
	private IPubSubConfig pubSubConfig;

	@Inject
	private IPubSubRepository repository;

	@Inject
	private PresenceCollectorModule presenceCollectorModule;

	@Inject
	private XsltTool xslTransformer;

	@Override
	public void checkPermission(BareJID serviceJid, String nodeName, JID senderJid, Action action)
			throws PubSubException, RepositoryException {
		if (nodeName == null || nodeName.isEmpty()) {
			if (isServiceJidPEP(serviceJid) && !serviceJid.equals(senderJid.getBareJID())) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}
			return;
		}

		AbstractNodeConfig nodeConfig = repository.getNodeConfig(serviceJid, nodeName);
		if (nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND);
		}

		if (pubSubConfig.isAdmin(senderJid)) {
			return;
		}

		if ((nodeConfig.getNodeAccessModel() == AccessModel.open) &&
				!Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains())) {
			throw new PubSubException(Authorization.FORBIDDEN);
		}

		IAffiliations nodeAffiliations = repository.getNodeAffiliations(serviceJid, nodeName);
		UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());
		if (senderAffiliation.getAffiliation() == Affiliation.outcast) {
			throw new PubSubException(Authorization.FORBIDDEN);
		}


		switch (action) {
			case subscribe:
				if (!senderAffiliation.getAffiliation().isSubscribe()) {
					throw new PubSubException(Authorization.FORBIDDEN, "Not enough privileges to subscribe");
				}
				Subscription subscription = repository.getNodeSubscriptions(serviceJid, nodeName).getSubscription(senderJid.getBareJID());
				if (subscription != null) {
					if ((subscription == Subscription.pending) && !(this.getPubSubConfig().isAdmin(senderJid) ||
							(senderAffiliation.getAffiliation() == Affiliation.owner))) {
						throw new PubSubException(Authorization.FORBIDDEN, PubSubErrorCondition.PENDING_SUBSCRIPTION,
												  "Subscription is pending");
					}
				}
				switch (nodeConfig.getNodeAccessModel()) {
					case presence:
						if (!hasSenderSubscription(senderJid.getBareJID(), nodeAffiliations)) {
							throw new PubSubException(Authorization.NOT_AUTHORIZED,
													  PubSubErrorCondition.PRESENCE_SUBSCRIPTION_REQUIRED);
						}
						break;
					case roster:
						if (!isSenderInRosterGroup(senderJid.getBareJID(), nodeConfig, nodeAffiliations, repository.getNodeSubscriptions(serviceJid, nodeName))) {
							throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_IN_ROSTER_GROUP);
						}
						break;
					case whitelist:
						switch (senderAffiliation.getAffiliation()) {
							case none:
							case outcast:
								throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
							default:
								break;
						}
					default:
						break;
				}
				break;
			case retrieveItems:
				switch (nodeConfig.getNodeAccessModel()) {
					case open:
						break;
					case whitelist:
						if (!senderAffiliation.getAffiliation().isRetrieveItem()) {
							throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
						}
						break;
					case authorize:
						Subscription senderSubscription = repository.getNodeSubscriptions(serviceJid, nodeName).getSubscription(senderJid.getBareJID());
						if (senderSubscription != Subscription.subscribed ||
								!senderAffiliation.getAffiliation().isRetrieveItem()) {
							throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_SUBSCRIBED);
						}
						break;
					case presence:
						if (!hasSenderSubscription(senderJid.getBareJID(), nodeAffiliations)) {
							throw new PubSubException(Authorization.NOT_AUTHORIZED,
													  PubSubErrorCondition.PRESENCE_SUBSCRIPTION_REQUIRED);
						}
						break;
					case roster:
						boolean allowed = isSenderInRosterGroup(senderJid.getBareJID(), nodeConfig, nodeAffiliations,
																repository.getNodeSubscriptions(serviceJid, nodeName));

						if (!allowed) {
							throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_IN_ROSTER_GROUP);
						}
						break;
				}
				break;
			case retractItems:
				if(!senderAffiliation.getAffiliation().isDeleteItem()) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
				break;
			case publishItems:
				if (!senderAffiliation.getAffiliation().isPublishItem()) {
					switch (nodeConfig.getPublisherModel()) {
						case open:
							break;
						case publishers:
							throw new PubSubException(Authorization.FORBIDDEN);
						case subscribers:
							if (repository.getNodeSubscriptions(serviceJid, nodeName).getSubscription(senderJid.getBareJID()) != Subscription.subscribed) {
								throw new PubSubException(Authorization.FORBIDDEN);
							}
							break;
					}
				}
				break;
			case purgeNode:
				switch (senderAffiliation.getAffiliation()) {
					case publisher:
					case owner:
						break;
					default:
						throw new PubSubException(Authorization.FORBIDDEN);
				}
//			case member:
//				switch (senderAffiliation.getAffiliation()) {
//					case publisher:
//					case member:
//					case owner:
//						break;
//					default:
//						throw new PubSubException(Authorization.FORBIDDEN);
//				}
			case manageNode:
				if (senderAffiliation.getAffiliation() != Affiliation.owner) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
				break;
		}
	}


	@Override
	public Element prepareNotificationMessage(JID from, String id, String uuid, String nodeName, List<Element> itemsToSend,
											  Map<String, String> headers) {
		Element message = new Element("message", new String[]{"xmlns", "from", "id"},
									  new String[]{Packet.CLIENT_XMLNS, from.toString(), id});
		Element event = new Element("event", new String[]{"xmlns"},
									new String[]{"http://jabber.org/protocol/pubsub#event"});

		if (uuid != null) {
			message.addChild(new Element("stanza-id", new String[]{"xmlns", "id", "by"},
										 new String[]{"urn:xmpp:sid:0", uuid, from.getBareJID().toString()}));
		}

		final Element items = new Element("items", new String[]{"node"}, new String[]{nodeName});
		items.addChildren(itemsToSend);
		event.addChild(items);
		if (itemsToSend.size() > 0) {
			String expireAt = itemsToSend.get(0).getAttributeStaticStr("expire-at");
			if (expireAt != null) {
				Element amp = new Element("amp");
				amp.setXMLNS(AMP_XMLNS);
				amp.addChild(new Element("rule", new String[]{"condition", "action", "value"}, new String[]{"expire-at", "drop", expireAt}));
				message.addChild(amp);
			}
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

		try {
			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(from.getBareJID(), nodeName);
			if ((this.xslTransformer != null) && (nodeConfig != null)) {
				for (Element itemToSend : itemsToSend) {
					try {
						List<Element> elems = this.xslTransformer.transform(itemToSend, nodeConfig);
						if (elems != null) {
							message.addChildren(elems);
						}
					} catch (Exception e) {
						log.log(Level.WARNING, "Problem with generating BODY", e);
					}
				}
			}
		} catch (RepositoryException ex) {
			// this should not happen and even if it can be ignored..
		}



		return message;
	}

	public boolean hasSenderSubscription(final BareJID bareJid, final IAffiliations affiliations) throws RepositoryException {

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
		final Stream<BareJID> subscribers = subscriptions.getSubscriptions().map(UsersSubscription::getJid);
		final String[] groupsAllowed = nodeConfig.getRosterGroupsAllowed();

		if ((groupsAllowed == null) || (groupsAllowed.length == 0)) {
			return true;
		}

		// @TODO - is there a way to optimize this?
		List<BareJID> owners = subscribers.filter(
				owner -> affiliations.getSubscriberAffiliation(owner).getAffiliation() == Affiliation.owner)
				.collect(Collectors.toList());

		if (owners.contains(bareJid)) {
			return true;
		}

		for (BareJID owner : owners) {
			Map<BareJID, RosterElement> buddies = repository.getUserRoster(owner);
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

	private static final Logger log = Logger.getLogger(DefaultPubSubLogic.class.getCanonicalName());

	protected Stream<JID> getActiveSubscribers(ISubscriptions subscriptions, final IAffiliations affiliations) {
		Stream<UsersSubscription> stream = subscriptions.getSubscriptionsForPublish();

		return stream.filter(subscription -> subscription.getSubscription() == Subscription.subscribed)
				.map(UsersSubscription::getJid)
				.filter(jid -> affiliations.getSubscriberAffiliation(jid).getAffiliation() != Affiliation.outcast)
				.map(JID::jidInstance);
	}

	@Override
	public Stream<JID> subscribersOfNotifications(BareJID serviceJid, String nodeName)
			throws RepositoryException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);
		IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(serviceJid, nodeName);
		ISubscriptions nodesSubscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeName);
		Stream<JID> stream = getActiveSubscribers(nodesSubscriptions, nodeAffiliations);

		if (nodeConfig.isPresenceExpired()) {
			final AtomicBoolean updateSubscriptions = new AtomicBoolean(false);
			JID[] filtered = stream.filter(jid -> {
				boolean available = this.presenceCollectorModule.isJidAvailable(serviceJid, jid.getBareJID());
				final UsersAffiliation afi = nodeAffiliations.getSubscriberAffiliation(jid.getBareJID());

				if ((afi == null) || (!available && (afi.getAffiliation() == Affiliation.member))) {
					nodesSubscriptions.changeSubscription(jid.getBareJID(), Subscription.none);
					updateSubscriptions.set(true);
					if (log.isLoggable(Level.FINE)) {
						log.fine("Subscriptione expired. Node: " + nodeConfig.getNodeName() + ", jid: " + jid);
					}
					return false;
				}
				return true;
			}).toArray(JID[]::new);

			if (updateSubscriptions.get()) {
				this.getRepository().update(serviceJid, nodeConfig.getNodeName(), nodesSubscriptions);
			}
			stream = Arrays.stream(filtered);
		}

		if (nodeConfig.isDeliverPresenceBased()) {
			stream = stream.flatMap(jid -> this.presenceCollectorModule.getAllAvailableResources(serviceJid, jid.getBareJID()).stream());
			
			// for pubsub service for user accounts we need dynamic
			// subscriptions based on presence
			boolean pep = isServiceJidPEP(serviceJid);
			if (pep || getPubSubConfig().isSubscribeByPresenceFilteredNotifications()) {
				switch (nodeConfig.getNodeAccessModel()) {
					case open:
					case presence:
						stream = Stream.concat(stream, this.presenceCollectorModule.getAllAvailableJidsWithFeature(serviceJid,
																					   nodeConfig.getNodeName() +
																							   "+notify").stream());
						break;
					case roster:
						String[] allowedGroups = nodeConfig.getRosterGroupsAllowed();
						if (allowedGroups != null && allowedGroups.length > 0) {
							Arrays.sort(allowedGroups);
							List<JID> jids = this.presenceCollectorModule.getAllAvailableJidsWithFeature(serviceJid,
																										 nodeConfig.getNodeName() +
																												 "+notify");
							if (!jids.isEmpty()) {
								Map<BareJID, RosterElement> roster = this.getRepository()
										.getUserRoster(serviceJid);
								stream = Stream.concat(stream, jids.stream().filter(jid -> {
									RosterElement re = roster.get(jid.getBareJID());
									return re != null && re.getGroups() != null && Arrays.stream(re.getGroups()).anyMatch(group -> Arrays.binarySearch(allowedGroups, group) < 0);
								}));
							}
						}
						break;
					case whitelist:
					default:
						break;
				}
			}
			if (pep) {
				stream = Stream.concat(this.presenceCollectorModule.getAllAvailableJidsWithFeature(serviceJid,
																								   nodeConfig.getNodeName() +
																										   "+notify")
											   .stream()
											   .filter(jid -> jid.getBareJID().equals(serviceJid)), stream);
			}
		}
		return stream;
	}
	
	@Override
	public boolean isMAMEnabled(BareJID serviceJid, String node) throws RepositoryException {
		if (isServiceJidPEP(serviceJid) && pubSubConfig.isMAMEnabled()) {
			return true;
		}
		return false;
	}

	protected IPubSubConfig getPubSubConfig() {
		return pubSubConfig;
	}

	protected IPubSubRepository getRepository() {
		return repository;
	}

	@Override
	public boolean isServiceJidPEP(BareJID serivceJid) {
		return pubSubConfig.isPepPeristent() && serivceJid.getLocalpart() != null &&
				!serivceJid.getDomain().startsWith(component.getName() + ".");
	}
}
