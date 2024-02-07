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
import tigase.db.util.SchemaManager;
import tigase.eventbus.EventBus;
import tigase.eventbus.HandleEvent;
import tigase.form.Field;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.PubSubLogic;
import tigase.pubsub.utils.executors.Executor;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.impl.roster.RosterAbstract.SubscriptionType;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.text.ParseException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Implementation of the PubSub component module responsible for publication of new entries on the pubsub nodes.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:21:54 GMT
 */
@Bean(name = "publishItemModule", parent = PubSubComponent.class, active = true)
public class PublishItemModule
		extends AbstractPubSubModule
		implements Initializable, UnregisterAware {

	public final static String AMP_XMLNS = "http://jabber.org/protocol/amp";
	public final static String[] SUPPORTED_PEP_XMLNS = {"http://jabber.org/protocol/mood",
														"http://jabber.org/protocol/geoloc",
														"http://jabber.org/protocol/activity",
														"http://jabber.org/protocol/tune"};
	private static final String[] FIELD_VALUE_PATH = { "field", "value" };
	private static final Criteria CRIT_PUBLISH = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"))
			.add(ElementCriteria.name("publish"));
	protected final LeafNodeConfig defaultPepNodeConfig;
	private final TimestampHelper dtf = new TimestampHelper();
	protected final Set<String> pepNodes = new HashSet<String>();
	@Inject
	private EventBus eventBus;
	private long idCounter = 0;
	@Inject
	private NotificationBroadcaster notificationBroadcaster;
	@Inject
	private PresenceCollectorModule presenceCollector;
	@Inject(nullAllowed = false)
	private IPubSubRepository repository;
	@Inject(bean = "publishExecutor")
	private Executor publishExecutor;
	private java.util.concurrent.ExecutorService eventExecutor;

	private static Collection<String> extractCDataItems(Element event, String[] path) {
		ArrayList<String> result = new ArrayList<>();
		List<Element> z = event.getChildren(path);
		if (z != null) {
			for (Element element : z) {
				if (element.getName().equals("item")) {
					result.add(element.getCData());
				}
			}
		}
		return result;
	}

	public static void main(String[] args) {
		System.out.println(".");
	}

	public PublishItemModule() {
		for (String xmlns : SUPPORTED_PEP_XMLNS) {
			pepNodes.add(xmlns);
		}
		// creating default config for autocreate PEP nodes
		this.defaultPepNodeConfig = new LeafNodeConfig("default-pep");
		defaultPepNodeConfig.setValue("pubsub#access_model", AccessModel.presence.name());
		defaultPepNodeConfig.setValue("pubsub#presence_based_delivery", true);
		defaultPepNodeConfig.setValue("pubsub#send_last_published_item", "on_sub_and_presence");
	}

	public void doPublishItems(BareJID serviceJID, String nodeName, LeafNodeConfig leafNodeConfig, String publisher,
							   List<Element> itemsToSend) throws RepositoryException, PubSubException {
		String uuid = null;
		if (leafNodeConfig.isPersistItem()) {
			if (pubSubLogic.isMAMEnabled(serviceJID, nodeName)) {
				uuid = UUID.randomUUID().toString().toLowerCase();
			}
			IItems nodeItems = getRepository().getNodeItems(serviceJID, nodeName);

			for (Element item : itemsToSend) {
				final String id = item.getAttributeStaticStr("id");

				if (!config.isPepRemoveEmptyGeoloc()) {
					nodeItems.writeItem(id, publisher, item, uuid);
				} else {
					Element geoloc = item.findChildStaticStr(new String[]{"item", "geoloc"});
					if (geoloc != null && (geoloc.getChildren() == null || geoloc.getChildren().size() == 0)) {
						nodeItems.deleteItem(id);
					} else {
						try {
							nodeItems.writeItem(id, publisher, item, uuid);
						} catch (RepositoryException ex) {
							if (log.isLoggable(Level.FINE)) {
								log.log(Level.FINE, "Could not store the item", ex);
							}
							throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "It was not possible to store the item", ex);
						}
					}
				}
			}
			Integer maxItems = leafNodeConfig.getMaxItems().getOrNull();
			if (maxItems != null) {
				trimItems(serviceJID, nodeName, maxItems, leafNodeConfig.getCollectionItemsOrdering());
			}
		}

		eventBus.fire(new ItemPublishedEvent(config.getComponentName(), serviceJID, nodeName, publisher, uuid, itemsToSend));
		generateItemsNotifications(serviceJID, nodeName, itemsToSend, uuid, pubSubLogic.isMAMEnabled(serviceJID, nodeName));
	}

	public void generateItemsNotifications(BareJID serviceJID, String nodeName, List<Element> itemsToSend, String uuid, boolean persistInMAM)
			throws RepositoryException {

		final Element items = new Element("items", new String[]{"node"}, new String[]{nodeName});
		items.addChildren(itemsToSend);

		generateNotifications(serviceJID, nodeName, items,
							  itemsToSend.isEmpty() ? null : itemsToSend.get(0).getAttributeStaticStr("id"),
							  itemsToSend.isEmpty() ? null : itemsToSend.get(0).getAttributeStaticStr("expire-at"),
							  uuid, persistInMAM);
	}

	public void generateNodeNotifications(BareJID serviceJID, String nodeName, Element payload, String uuid, boolean persistInMAM) throws RepositoryException {
		generateNotifications(serviceJID, nodeName, payload, null, null, uuid, persistInMAM);
	}

	private void generateNotifications(BareJID serviceJID, String nodeName, Element payload, String itemId, String expireAt, String uuid, boolean persistInMAM) throws RepositoryException {
		for (SchemaManager.Pair<String, StanzaType> pair : getCollectionsForNotification(serviceJID, nodeName)) {
			Map<String, String> headers = null;
			if (pair.getKey() != null) {
				headers = new HashMap<>();
				headers.put("Collection", pair.getKey());
			}
			Element message = pubSubLogic.prepareNotificationMessage(JID.jidInstance(serviceJID), uuid == null ? String.valueOf(++counter) : uuid, uuid, nodeName, payload, expireAt, headers, pair.getValue());

			// JUST AN IDEA: MAM2 should work only for leaf nodes... (full query of subtrees is complicated and makes it impossible to satisfy constraints!
			// MAM cannot work with batch publication of items and this should be forbidden (bad-request)
			// specification of "from", "to", and "id" are optional
			// most likely message should have a "stable-id" assigned, but ID can be different
			// what should control if MAM is enabled? = !isPEP && (pubsubConfig && pubsubNodeConfig)

			// should we store "retraction" notifications? or even "collection reassignments"?
			// we should not according to the XEP-0313, while it would be useful.. (at least retractions)
			if (uuid != null && persistInMAM) {
				getRepository().addMAMItem(serviceJID, pair.getKey() == null ? nodeName : pair.getKey(), uuid, message, itemId);
			}
			eventBus.fire(new BroadcastNotificationEvent(config.getComponentName(), serviceJID, nodeName, message));

			// TODO: priority is for now set to `normal` but we should consider retrieving it from the PubSub node configuration
			broadcastNotification(Executor.Priority.normal, serviceJID, nodeName, message);
		}
	}

	public void sendNotification(BareJID serviceJID, String nodeName, Element item, String uuid, Map<String,String> headers, JID recipient, StanzaType stanzaType) {
		final Element items = new Element("items", new String[]{"node"}, new String[]{nodeName});
		items.addChild(item);
		
		Element message = pubSubLogic.prepareNotificationMessage(JID.jidInstance(serviceJID), uuid == null ? String.valueOf(++counter) : uuid, uuid, nodeName, items, null, headers, stanzaType);
		packetWriter.write(Packet.packetInstance(message, JID.jidInstance(serviceJID), recipient));
	}

	public void broadcastNotification(Executor.Priority priority, BareJID serviceJID, String nodeName, Element message)
			throws RepositoryException {
		notificationBroadcaster.broadcastNotification(priority, serviceJID, nodeName, message);
	}

	public AbstractNodeConfig ensurePepNode(BareJID toJid, String nodeName, BareJID ownerJid, Element publishOptions) throws PubSubException {
		AbstractNodeConfig nodeConfig;
		try {
			IPubSubRepository repo = getRepository();
			nodeConfig = repo.getNodeConfig(toJid, nodeName);
		} catch (RepositoryException ex) {
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "Error occured during autocreation of node",
									  ex);
		}

		if (nodeConfig != null) {
			return nodeConfig;
		}

		return createPepNode(toJid, nodeName, ownerJid, publishOptions);
	}

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#publish",
							"http://jabber.org/protocol/pubsub#publish-options",
							"http://jabber.org/protocol/pubsub#multi-items",
							"http://jabber.org/protocol/pubsub#item-ids",
							"http://jabber.org/protocol/pubsub#persistent-items"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_PUBLISH;
	}

	private List<SchemaManager.Pair<String,StanzaType>> getCollectionsForNotification(final BareJID serviceJid, final String nodeName) throws RepositoryException {
		ArrayList<SchemaManager.Pair<String,StanzaType>> result = new ArrayList<>();
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);
		String cn = nodeConfig.getCollection();

		result.add(new SchemaManager.Pair(null, nodeConfig.getNotificationType()));

		while ((cn != null) && !"".equals(cn)) {

			AbstractNodeConfig nc = getRepository().getNodeConfig(serviceJid, cn);
			result.add(new SchemaManager.Pair(cn, nc.getNotificationType()));

			cn = nc.getCollection();
		}

		return result;
	}

	@Override
	public void initialize() {
		if (eventBus != null) {
			eventBus.registerAll(this);
		} else {
			log.warning("EventBus is not injected!");
		}
		eventExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
	}

	public boolean isPEPNodeName(String nodeName) {
		if (config.isPepPeristent()) {
			return false;
		}

		return this.pepNodes.contains(nodeName);
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		if (publishExecutor.isOverloaded()) {
			throw new PubSubException(Authorization.RESOURCE_CONSTRAINT);
		}

		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element publish = pubSub.getChild("publish");
		final Element publishOptions = Optional.ofNullable(pubSub.getChild("publish-options"))
				.map(el -> el.getChild("x", "jabber:x:data"))
				.orElse(null);
		final String nodeName = publish.getAttributeStaticStr("node");

		try {
			if (isPEPNodeName(nodeName)) {
				pepProcess(packet, pubSub, publish);
				return;
			}

			pubSubLogic.checkPermission(toJid, nodeName, packet.getStanzaFrom(), PubSubLogic.Action.publishItems);

			List<Element> itemsToSend = makeItemsToSend(publish);
			List<String> itemIds = publishItems(toJid, nodeName, packet.getStanzaFrom(), itemsToSend, publishOptions);

			final Packet resultIq = packet.okResult((Element) null, 0);

			if (itemIds != null) {
				Element resPubsub = new Element("pubsub", new String[]{"xmlns"},
												new String[]{"http://jabber.org/protocol/pubsub"});

				resultIq.getElement().addChild(resPubsub);

				Element resPublish = new Element("publish", new String[]{"node"}, new String[]{nodeName});

				resPubsub.addChild(resPublish);
				itemIds.stream().map(id -> new Element("item", new String[]{"id"}, new String[]{id})).forEach(resPublish::addChild);
			}
			packetWriter.write(resultIq);
		} catch (PubSubException e1) {
			throw e1;
		} catch (RepositoryException e1) {
			log.log(Level.FINE, "Error processing publish packet", e1);
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "Error processing publish packet", e1);
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing publish packet", e);

			throw new RuntimeException(e);
		}
	}


	public List<String> publishItems(BareJID toJid, String nodeName, JID publisher, List<Element> itemsToPublish, Element publishOptions) throws RepositoryException, PubSubException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);

		if (nodeConfig == null) {
			if ((!pubSubLogic.isServiceJidPEP(toJid)) || !config.isPepPeristent()) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			} else {
				// this is PubSub service for particular user - we should
				// autocreate node
				nodeConfig = createPepNode(toJid, nodeName, publisher.getBareJID(), publishOptions);
			}
		} else {
			if (nodeConfig.getNodeType() == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED,
										  new PubSubErrorCondition("unsupported", "publish"));
			}
		}
		
		if (publishOptions != null) {
			if (publishOptions.findChild(
					el -> el.getName() == "field" && "FORM_TYPE" == el.getAttributeStaticStr("var") &&
							"http://jabber.org/protocol/pubsub#publish-option".equals(
									el.getCData(FIELD_VALUE_PATH))) == null) {
				for (Element field : publishOptions.getChildren()) {
					String key = field.getAttributeStaticStr("var");
					if ("FORM_TYPE".equals(key)) {
						continue;
					}

					Field f = nodeConfig.getForm().get(key);
					if (f == null) {
						throw new PubSubException(Authorization.CONFLICT,
												  PubSubErrorCondition.PRECONDITION_NOT_MET);
					}
					switch (f.getType()) {
						case bool:
							String v1 = field.getCData(FIELD_VALUE_PATH);
							if (!(("true".equals(v1) || "1".equals(v1)) ==
									("true".equals(f.getValue()) || "1".equals(f.getValue())))) {
								throw new PubSubException(Authorization.CONFLICT,
														  PubSubErrorCondition.PRECONDITION_NOT_MET);
							}
							break;
						case jid_multi:
						case text_multi:
							List<String> reqValues = Optional.ofNullable(
									field.mapChildren(el -> el.getName() == "value", el -> el.getCData()))
									.orElse(Collections.EMPTY_LIST);
							String[] values = f.getValues();
							if (values == null) {
								if (!reqValues.isEmpty()) {
									throw new PubSubException(Authorization.CONFLICT,
															  PubSubErrorCondition.PRECONDITION_NOT_MET);
								} else {
									continue;
								}
							}
							if (reqValues.size() != values.length) {
								throw new PubSubException(Authorization.CONFLICT,
														  PubSubErrorCondition.PRECONDITION_NOT_MET);
							}
							for (String v2 : values) {
								if (!reqValues.contains(v2)) {
									throw new PubSubException(Authorization.CONFLICT,
															  PubSubErrorCondition.PRECONDITION_NOT_MET);
								}
							}
							break;
						default:
							String reqValue = field.getCData(FIELD_VALUE_PATH);
							String value = f.getValue();
							if (!((reqValue == null && value == null) ||
									(reqValue != null && reqValue.equals(value)))) {
								throw new PubSubException(Authorization.CONFLICT,
														  PubSubErrorCondition.PRECONDITION_NOT_MET);
							}
							break;
					}
				}
			}
		}

		LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;
		if (pubSubLogic.isMAMEnabled(toJid, nodeName) && itemsToPublish.size() > 1) {
			throw new PubSubException(Authorization.NOT_ALLOWED, "Bulk publication not allowed");
		}

		List<String> itemIds = null;
		if (leafNodeConfig.isPersistItem()) {
			itemIds = new ArrayList<>();
			for (Element item : itemsToPublish) {
				String id = pubSubLogic.validateItemId(toJid, nodeName, item.getAttributeStaticStr("id"));

				if (!id.equals(item.getAttributeStaticStr("id"))) {
					item.setAttribute("id", id);
				}
				itemIds.add(id);
			}
		}
		
		doPublishItems(toJid, nodeName, leafNodeConfig, publisher.toString(), itemsToPublish);

		return itemIds;
	}

	public void publishLastItem(BareJID serviceJid, AbstractNodeConfig nodeConfig, JID destinationJID)
			throws RepositoryException {
		try {
			pubSubLogic.checkPermission(serviceJid, nodeConfig.getNodeName(), destinationJID, PubSubLogic.Action.retrieveItems);
		} catch (Exception ex) {
			return;
		}

		IItems nodeItems = this.getRepository().getNodeItems(serviceJid, nodeConfig.getNodeName());
		if (nodeItems != null) {
			String[] ids = nodeItems.getItemsIds(nodeConfig.getCollectionItemsOrdering());

			if (ids != null && ids.length > 0) {
				String lastID = ids[ids.length - 1];
				IItems.IItem item = nodeItems.getItem(lastID);
				if (item != null && item.getItem() != null) {
					sendNotification(serviceJid, nodeConfig.getNodeName(), item.getItem(), item.getUUID(), null, destinationJID, nodeConfig.getNotificationType());
				} else {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "There is no payload for item with id '" + lastID + "' at '" + nodeConfig.getNodeName() +
								"' for '" + serviceJid + "'");
					}
				}
			}
		}
	}
	
	public void trimItems(final BareJID serviceJid, final String nodeName, final Integer maxItems, CollectionItemsOrdering collectionItemsOrdering) throws RepositoryException {
		IItems nodeItems = getRepository().getNodeItems(serviceJid, nodeName);
		final String[] ids = nodeItems.getItemsIds(collectionItemsOrdering);

		if ((ids == null) || (ids.length <= maxItems)) {
			return;
		}
		
		for (int i = 0; i < (ids.length - maxItems); i++) {
			String id = ids[i];

			nodeItems.deleteItem(id);
		}
	}

	@Override
	public void beforeUnregister() {
		if (eventExecutor != null) {
			eventExecutor.shutdown();
		}
		if (eventBus != null) {
			eventBus.unregisterAll(this);
		}
	}
	
	protected JID[] getValidBuddies(BareJID id) throws RepositoryException {
		ArrayList<JID> result = new ArrayList<JID>();
		Map<BareJID, RosterElement> rosterJids = this.getRepository().getUserRoster(id);

		if (rosterJids != null) {
			for (Entry<BareJID, RosterElement> e : rosterJids.entrySet()) {
				SubscriptionType sub = e.getValue().getSubscription();

				if (sub == SubscriptionType.both || sub == SubscriptionType.from ||
						sub == SubscriptionType.from_pending_out) {
					result.add(JID.jidInstance(e.getKey()));
				}
			}
		}

		return result.toArray(new JID[]{});
	}

	@HandleEvent
	protected void onCapsChange(PresenceCollectorModule.CapsChangeEvent event) throws TigaseStringprepException {
		if (!event.componentName.equals(config.getComponentName())) {
			return;
		}
		final Collection<String> newFeatures = event.newFeatures;

		if (newFeatures == null || newFeatures.isEmpty() || !config.isSendLastPublishedItemOnPresence()) {
			return;
		}

		// if we have new features we need to check if there are nodes for
		// which
		// we need to send notifications due to +notify feature
		for (String feature : newFeatures) {
			if (!feature.endsWith("+notify")) {
				continue;
			}

			String nodeName = feature.substring(0, feature.length() - "+notify".length());
			capsSendLastPublishedItem(event.serviceJid, nodeName, event.buddyJid);
		}
	}

	private void capsSendLastPublishedItem(BareJID serviceJid, String nodeName, JID buddyJid) {
		eventExecutor.execute(() -> {
			try {
				ISubscriptions subscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeName);
				if (subscriptions != null) {
					if (subscriptions.getSubscription(buddyJid.getBareJID()) == Subscription.subscribed) {
						// user is already subcribed to this node and will receive notifications event without CAPS change based delivery
						return;
					}
				}
				publishLastItem(serviceJid, nodeName, buddyJid);
			} catch (RepositoryException ex) {
				log.log(Level.WARNING,
						"Exception while sending last published item on on_sub_and_presence for service jid " +
								serviceJid + " and node " + nodeName);
			}
		});
	}

	protected void publishLastItem(BareJID serviceJid, String nodeName, JID buddyJid) throws RepositoryException {
		AbstractNodeConfig nodeConfig = repository.getNodeConfig(serviceJid, nodeName);
		if (nodeConfig != null && nodeConfig.getSendLastPublishedItem() == SendLastPublishedItem.on_sub_and_presence) {
			if (nodeConfig instanceof LeafNodeConfig) {
				publishLastItem(serviceJid, nodeConfig, buddyJid);
			} else if (nodeConfig instanceof CollectionNodeConfig) {
				String[] childNodes = repository.getChildNodes(serviceJid, nodeConfig.getNodeName());
				if (childNodes != null) {
					for (String childNode : childNodes) {
						try {
							publishLastItem(serviceJid, childNode, buddyJid);
						} catch (RepositoryException ex) {
							log.log(Level.WARNING,
									"Exception while sending last published item on on_sub_and_presence for service jid " +
											serviceJid + " and node " + childNode);
						}
					}
				}
			}
		}
	}

	@HandleEvent
	protected void onPresenceChangeEvent(PresenceCollectorModule.PresenceChangeEvent event)
			throws TigaseStringprepException {
		if (!event.componentName.equals(config.getComponentName())) {
			return;
		}

		Packet packet = event.packet;
		// PEP services are using CapsChangeEvent - but we should process
		// this here as well
		// as on PEP service we can have some nodes which have there types
		// of subscription
		if (packet.getStanzaTo() == null) // ||
		// packet.getStanzaTo().getLocalpart()
		// != null)
		{
			return;
		}
		if (!config.isSendLastPublishedItemOnPresence()) {
			return;
		}
		if (packet.getType() == null || packet.getType() == StanzaType.available) {
			sendLastPublishedItemFromSubscribedNodes(packet.getStanzaTo().getBareJID(), packet.getStanzaFrom());
		}

	}

	private void sendLastPublishedItemFromSubscribedNodes(BareJID serviceJid, JID userJid) {
		eventExecutor.execute(() -> {
			try {
				// sending last published items for subscribed nodes
				Map<String, UsersSubscription> subscrs = repository.getUserSubscriptions(serviceJid,
																						 userJid.getBareJID());
				log.log(Level.FINEST, "Sending last published items for subscribed nodes: {0}", subscrs);
				for (Map.Entry<String, UsersSubscription> e : subscrs.entrySet()) {
					if (e.getValue().getSubscription() != Subscription.subscribed) {
						continue;
					}
					String nodeName = e.getKey();
					AbstractNodeConfig nodeConfig = repository.getNodeConfig(serviceJid, nodeName);
					if (nodeConfig.getSendLastPublishedItem() != SendLastPublishedItem.on_sub_and_presence) {
						continue;
					}
					publishLastItem(serviceJid, nodeConfig, userJid);
				}
			} catch (RepositoryException ex) {
				log.log(Level.SEVERE, "Problem retrieving data from repository", ex);
			}
		});
	}

	private AbstractNodeConfig createPepNode(BareJID toJid, String nodeName, BareJID ownerJid, Element publishOptions) throws PubSubException {
		if (!toJid.equals(ownerJid)) {
			throw new PubSubException(Authorization.FORBIDDEN);
		}

		AbstractNodeConfig nodeConfig;
		try {
			IPubSubRepository repo = getRepository();
			nodeConfig = new LeafNodeConfig(nodeName, defaultPepNodeConfig);
			if (publishOptions != null) {
				for (Element field : publishOptions.getChildren()) {
					String key = field.getAttributeStaticStr("var");
					if ("http://jabber.org/protocol/pubsub#publish-option".equals(key)) {
						continue;
					}

					List<String> values = field.mapChildren(el -> el.getName() == "value", el -> el.getCData());
					Field f = nodeConfig.getForm().get(key);
					if (f == null) {
						throw new PubSubException(Authorization.BAD_REQUEST, "Invalid option " + key);
					}
					f.setValues(values == null ? null : values.toArray(new String[values.size()]));
				}
			}
			repo.createNode(toJid, nodeName, ownerJid, nodeConfig, NodeType.leaf, "");
			nodeConfig = repo.getNodeConfig(toJid, nodeName);
			IAffiliations nodeaAffiliations = repo.getNodeAffiliations(toJid, nodeName);
			nodeaAffiliations.addAffiliation(ownerJid, Affiliation.owner);
			ISubscriptions nodeaSubscriptions = repo.getNodeSubscriptions(toJid, nodeName);
			if (config.isAutoSubscribeNodeCreator()) {
				nodeaSubscriptions.addSubscriberJid(toJid, Subscription.subscribed);
			}
			repo.update(toJid, nodeName, nodeaAffiliations);
			repo.addToRootCollection(toJid, nodeName);
			log.log(Level.FINEST, "Created new PEP node: {0}, conf: {1}, aff: {2}, subs: {3} ",
					new Object[]{nodeName, nodeConfig, nodeaAffiliations, nodeaSubscriptions});
		} catch (RepositoryException ex) {
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "Error occured during autocreation of node",
									  ex);
		}
		return nodeConfig;
	}

	private List<Element> makeItemsToSend(Element publish) throws PubSubException {
		List<Element> items = new ArrayList<Element>();

		for (Element si : publish.getChildren()) {
			if (!"item".equals(si.getName())) {
				continue;
			}
			String expireAttr = si.getAttributeStaticStr("expire-at");
			if (expireAttr != null) {
				try {
					Date parseDateTime = dtf.parseTimestamp(expireAttr);
					if (null != parseDateTime) {
						si.setAttribute("expire-at", dtf.format(parseDateTime));
					} else {
						si.removeAttribute("expire-at");
					}
				} catch (ParseException e) {
					throw new PubSubException(Authorization.BAD_REQUEST, "Invalid value for attribute expire-at");
				}
			}
			items.add(si);
		}

		return items;
	}

	private void pepProcess(final Packet packet, final Element pubSub, final Element publish)
			throws RepositoryException {
		final JID senderJid = packet.getStanzaFrom();
		JID[] subscribers = getValidBuddies(senderJid.getBareJID());

		final Element item = publish.getChild("item");
		String nodeName = publish.getAttributeStaticStr("node");
		final Element items = new Element("items", new String[]{"node"}, new String[]{nodeName});
		items.addChild(item);
		
		Element message = pubSubLogic.prepareNotificationMessage(senderJid.copyWithoutResource(), String.valueOf(++counter), null, nodeName, items, null, null, StanzaType.headline);

		for (JID jid : subscribers) {
			Element clone = message.clone();
			packetWriter.write(Packet.packetInstance(clone, senderJid, jid));
		}

		packetWriter.write(packet.okResult((Element) null, 0));
		packetWriter.write(Packet.packetInstance(message, senderJid, senderJid));
	}

	private static class Item {

		final String id;
		final Date updateDate;

		Item(String id, Date date) {
			this.updateDate = date;
			this.id = id;
		}
	}

	public static class ItemPublishedEvent {

		public final String componentName;
		public final List<Element> itemsToSend;
		public final String uuid;
		public final String node;
		public final String publisher;
		public final BareJID serviceJid;

		public ItemPublishedEvent(String componentName, BareJID serviceJid, String node, String publisher, String uuid, List<Element> itemsToSend) {
			this.componentName = componentName;
			this.serviceJid = serviceJid;
			this.node = node;
			this.publisher = publisher;
			this.itemsToSend = itemsToSend;
			this.uuid = uuid;
		}

	}

	public static class BroadcastNotificationEvent {

		public final String componentName;
		public final BareJID serviceJid;
		public final String node;
		public final Element notificationMessage;

		public BroadcastNotificationEvent(String componentName, BareJID serviceJid, String node, Element notificationMessage) {
			this.componentName = componentName;
			this.serviceJid = serviceJid;
			this.node = node;
			this.notificationMessage = notificationMessage;
		}
	}
}
