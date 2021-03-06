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
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
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
import java.util.logging.Level;
import java.util.logging.Logger;

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
	private final LeafNodeConfig defaultPepNodeConfig;
	private final TimestampHelper dtf = new TimestampHelper();
	private final Set<String> pepNodes = new HashSet<String>();
	@Inject
	private EventBus eventBus;
	private long idCounter = 0;
	@Inject
	private PresenceCollectorModule presenceCollector;
	@Inject(nullAllowed = false)
	private IPubSubRepository repository;
	@Inject
	private XsltTool xslTransformer;

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
							   List<Element> itemsToSend) throws RepositoryException {
		if (leafNodeConfig.isPersistItem()) {
			IItems nodeItems = getRepository().getNodeItems(serviceJID, nodeName);

			for (Element item : itemsToSend) {
				final String id = item.getAttributeStaticStr("id");

				if (!config.isPepRemoveEmptyGeoloc()) {
					nodeItems.writeItem(System.currentTimeMillis(), id, publisher, item);
				} else {
					Element geoloc = item.findChildStaticStr(new String[]{"item", "geoloc"});
					if (geoloc != null && (geoloc.getChildren() == null || geoloc.getChildren().size() == 0)) {
						nodeItems.deleteItem(id);
					} else {
						nodeItems.writeItem(System.currentTimeMillis(), id, publisher, item);
					}
				}
			}
			if (leafNodeConfig.getMaxItems() != null) {
				trimItems(nodeItems, leafNodeConfig.getMaxItems(), leafNodeConfig.getCollectionItemsOrdering());
			}
		}

		eventBus.fire(new ItemPublishedEvent(serviceJID, nodeName, publisher, itemsToSend));
		sendNotifications(serviceJID, nodeName, itemsToSend);
	}

	public void sendNotifications(BareJID serviceJID, String nodeName, List<Element> itemsToSend)
			throws RepositoryException {
		final Element items = new Element("items", new String[]{"node"}, new String[]{nodeName});

		AbstractNodeConfig leafNodeConfig = getRepository().getNodeConfig(serviceJID, nodeName);
		IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(serviceJID, nodeName);
		ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJID, nodeName);

		items.addChildren(itemsToSend);
		sendNotifications(items, JID.jidInstance(serviceJID), nodeName,
						  this.getRepository().getNodeConfig(serviceJID, nodeName), nodeAffiliations,
						  nodeSubscriptions);

		List<String> parents = getParents(serviceJID, nodeName);

		log.log(Level.FINEST, "Publishing item: {0}, node: {1}, conf: {2}, aff: {3}, subs: {4} ",
				new Object[]{items, nodeName, leafNodeConfig, nodeAffiliations, nodeSubscriptions});

		if ((parents != null) && (parents.size() > 0)) {
			for (int i=1; i<=parents.size(); i++) {
				String collection = parents.get(i-1);
				Map<String, String> headers = new HashMap<String, String>();

				headers.put("Collection", collection);

				AbstractNodeConfig colNodeConfig = this.getRepository().getNodeConfig(serviceJID, collection);
				ISubscriptions colNodeSubscriptions = this.getRepository().getNodeSubscriptions(serviceJID, collection);
				IAffiliations colNodeAffiliations = this.getRepository().getNodeAffiliations(serviceJID, collection);

				sendNotifications(items, JID.jidInstance(serviceJID), nodeName, headers, colNodeConfig,
								  colNodeAffiliations, colNodeSubscriptions, i);
			}
		}
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
		return new String[]{"http://jabber.org/protocol/pubsub#publish", "http://jabber.org/protocol/pubsub#publish-options"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_PUBLISH;
	}

	public List<String> getParents(final BareJID serviceJid, final String nodeName) throws RepositoryException {
		ArrayList<String> result = new ArrayList<String>();
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);
		String cn = nodeConfig.getCollection();

		while ((cn != null) && !"".equals(cn)) {
			result.add(cn);

			AbstractNodeConfig nc = getRepository().getNodeConfig(serviceJid, cn);

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
	}

	public boolean isPEPNodeName(String nodeName) {
		if (config.isPepPeristent()) {
			return false;
		}

		return this.pepNodes.contains(nodeName);
	}

	@Override
	public void process(Packet packet) throws PubSubException {
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

			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);
			
			if (nodeConfig == null) {
				if (packet.getStanzaTo().getLocalpart() == null || !config.isPepPeristent()) {
					throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
				} else {
					// this is PubSub service for particular user - we should
					// autocreate node
					nodeConfig = createPepNode(toJid, nodeName, packet.getStanzaFrom().getBareJID(), publishOptions);
				}
			} else {
				if (nodeConfig.getNodeType() == NodeType.collection) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED,
											  new PubSubErrorCondition("unsupported", "publish"));
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
								throw new PubSubException(Authorization.CONFLICT, PubSubErrorCondition.PRECONDITION_NOT_MET);
							}
							switch (f.getType()) {
								case bool:
									String v1 = field.getCData(FIELD_VALUE_PATH);
									if (!(("true".equals(v1) || "1".equals(v1)) == ("true".equals(f.getValue()) || "1".equals(f.getValue())))) {
										throw new PubSubException(Authorization.CONFLICT, PubSubErrorCondition.PRECONDITION_NOT_MET);
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
											throw new PubSubException(Authorization.CONFLICT, PubSubErrorCondition.PRECONDITION_NOT_MET);
										} else {
											continue;
										}
									}
									if (reqValues.size() != values.length) {
										throw new PubSubException(Authorization.CONFLICT, PubSubErrorCondition.PRECONDITION_NOT_MET);
									}
									for (String v2 : values) {
										if (!reqValues.contains(v2)) {
											throw new PubSubException(Authorization.CONFLICT, PubSubErrorCondition.PRECONDITION_NOT_MET);
										}
									}
									break;
								default:
									String reqValue = field.getCData(FIELD_VALUE_PATH);
									String value = f.getValue();
									if (!((reqValue == null && value == null) || (reqValue != null && reqValue.equals(value)))) {
										throw new PubSubException(Authorization.CONFLICT, PubSubErrorCondition.PRECONDITION_NOT_MET);
									}
									break;
							}
						}
					}
				}
			}

			IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(toJid, nodeName);
			final UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(
					packet.getStanzaFrom().getBareJID());
			final ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, nodeName);

			// XXX #125
			final PublisherModel publisherModel = nodeConfig.getPublisherModel();

			if (!senderAffiliation.getAffiliation().isPublishItem() && !config.isAdmin(packet.getStanzaFrom())) {
				if ((publisherModel == PublisherModel.publishers) || ((publisherModel == PublisherModel.subscribers) &&
						(nodeSubscriptions.getSubscription(packet.getStanzaFrom().getBareJID()) !=
								Subscription.subscribed))) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
			}

			LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;
			List<Element> itemsToSend = makeItemsToSend(publish);
			final Packet resultIq = packet.okResult((Element) null, 0);

			if (leafNodeConfig.isPersistItem()) {

				// checking ID
				Element resPubsub = new Element("pubsub", new String[]{"xmlns"},
												new String[]{"http://jabber.org/protocol/pubsub"});

				resultIq.getElement().addChild(resPubsub);

				Element resPublish = new Element("publish", new String[]{"node"}, new String[]{nodeName});

				resPubsub.addChild(resPublish);
				for (Element item : itemsToSend) {
					String id = item.getAttributeStaticStr("id");

					if (id == null) {
						id = Utils.createUID();

						// throw new PubSubException(Authorization.BAD_REQUEST,
						// PubSubErrorCondition.ITEM_REQUIRED);
						item.setAttribute("id", id);
					}
					resPublish.addChild(new Element("item", new String[]{"id"}, new String[]{id}));
				}
			}
			packetWriter.write(resultIq);

			doPublishItems(toJid, nodeName, leafNodeConfig, element.getAttributeStaticStr("from"), itemsToSend);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing publish packet", e);

			throw new RuntimeException(e);
		}
	}
	
	public void publishLastItem(BareJID serviceJid, AbstractNodeConfig nodeConfig, JID destinationJID)
			throws RepositoryException {
		try {
			IAffiliations affiliations = getRepository().getNodeAffiliations(serviceJid, nodeConfig.getNodeName());
			ISubscriptions subscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeConfig.getNodeName());
			logic.checkAccessPermission(serviceJid, nodeConfig, affiliations, subscriptions, destinationJID);
		} catch (Exception ex) {
			return;
		}
		IItems nodeItems = this.getRepository().getNodeItems(serviceJid, nodeConfig.getNodeName());
		String[] ids = nodeItems.getItemsIds(nodeConfig.getCollectionItemsOrdering());

		if (ids != null && ids.length > 0) {
			String lastID = ids[ids.length - 1];
			Element payload = nodeItems.getItem(lastID);
			if (payload != null) {
				Element items = new Element("items");
				items.addAttribute("node", nodeConfig.getNodeName());
				items.addChild(payload);

				sendNotifications(new JID[]{destinationJID}, items, JID.jidInstance(serviceJid), nodeConfig, nodeConfig.getNodeName(), null);
			} else {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST,
							"There is no payload for item with id '" + lastID + "' at '" + nodeConfig.getNodeName() +
									"' for '" + serviceJid + "'");
				}
			}
		}

	}

	public void sendNotifications(Element itemToSend, final JID jidFrom, final String publisherNodeName,
								  AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations,
								  ISubscriptions nodesSubscriptions) throws RepositoryException {
		sendNotifications(itemToSend, jidFrom, publisherNodeName, null, nodeConfig, nodeAffiliations,
						  nodesSubscriptions, 0);
	}

	public void sendNotifications(final Element itemToSend, final JID jidFrom, final String publisherNodeName,
								  final Map<String, String> headers, AbstractNodeConfig nodeConfig,
								  IAffiliations nodeAffiliations, ISubscriptions nodesSubscriptions, int depth)
			throws RepositoryException {
		if (depth > 1) {
			return;
		}

		beforePrepareNotification(nodeConfig, nodesSubscriptions);

		HashSet<JID> tmp = new HashSet<JID>();
		for (BareJID j : getActiveSubscribers(nodeConfig, nodeAffiliations, nodesSubscriptions)) {
			tmp.add(JID.jidInstance(j));
		}
		boolean updateSubscriptions = false;

		log.log(Level.FINEST,
				"Sending notifications[1] item: {0}, node: {1}, conf: {2}, aff: {3}, subs: {4}, getActiveSubscribers: {5} ",
				new Object[]{itemToSend, publisherNodeName, nodeConfig, nodeAffiliations, nodesSubscriptions, tmp});

		if (nodeConfig.isPresenceExpired()) {
			Iterator<JID> it = tmp.iterator();

			while (it.hasNext()) {
				final JID jid = it.next();
				boolean available = this.presenceCollector.isJidAvailable(jidFrom.getBareJID(), jid.getBareJID());
				final UsersAffiliation afi = nodeAffiliations.getSubscriberAffiliation(jid.getBareJID());

				if ((afi == null) || (!available && (afi.getAffiliation() == Affiliation.member))) {
					it.remove();
					nodesSubscriptions.changeSubscription(jid.getBareJID(), Subscription.none);
					updateSubscriptions = true;
					if (log.isLoggable(Level.FINE)) {
						log.fine("Subscriptione expired. Node: " + nodeConfig.getNodeName() + ", jid: " + jid);
					}
				}
			}
		}
		if (updateSubscriptions) {
			this.getRepository().update(jidFrom.getBareJID(), nodeConfig.getNodeName(), nodesSubscriptions);
		}

		JID[] subscribers = tmp.toArray(new JID[]{});

		if (nodeConfig.isDeliverPresenceBased()) {
			HashSet<JID> s = new HashSet<JID>();

			for (JID jid : subscribers) {
				s.addAll(this.presenceCollector.getAllAvailableResources(jidFrom.getBareJID(), jid.getBareJID()));
			}

			// for pubsub service for user accounts we need dynamic
			// subscriptions based on presence
			if (jidFrom.getLocalpart() != null || config.isSubscribeByPresenceFilteredNotifications()) {
				switch (nodeConfig.getNodeAccessModel()) {
					case open:
					case presence:
						s.addAll(this.presenceCollector.getAllAvailableJidsWithFeature(jidFrom.getBareJID(),
																					   nodeConfig.getNodeName() +
																							   "+notify"));
						break;
					case roster:
						String[] allowedGroups = nodeConfig.getRosterGroupsAllowed();
						Arrays.sort(allowedGroups);
						List<JID> jids = this.presenceCollector.getAllAvailableJidsWithFeature(jidFrom.getBareJID(),
																							   nodeConfig.getNodeName() +
																									   "+notify");
						if (!jids.isEmpty() && (allowedGroups != null && allowedGroups.length > 0)) {
							Map<BareJID, RosterElement> roster = this.getRepository()
									.getUserRoster(jidFrom.getBareJID());
							Iterator<JID> it = jids.iterator();
							for (JID jid : jids) {
								RosterElement re = roster.get(jid.getBareJID());
								if (re == null) {
									it.remove();
									continue;
								}
								boolean notInGroups = true;
								String[] groups = re.getGroups();
								if (groups != null) {
									for (String group : groups) {
										notInGroups &= Arrays.binarySearch(allowedGroups, group) < 0;
									}
								}
								if (notInGroups) {
									it.remove();
								}
							}
						}
						break;
					default:
						break;
				}
			}
			subscribers = s.toArray(new JID[]{});
		}

		sendNotifications(subscribers, itemToSend, jidFrom, nodeConfig, publisherNodeName, headers);
	}

	public void sendNotifications(final JID[] subscribers, final Element itemToSend, final JID jidFrom,
								  AbstractNodeConfig nodeConfig, final String publisherNodeName,
								  final Map<String, String> headers) {
		List<Element> body = null;

		log.log(Level.FINEST, "Sending notifications[2] item: {0}, node: {1}, conf: {2}, subs: {3} ",
				new Object[]{itemToSend, publisherNodeName, nodeConfig, Arrays.asList(subscribers)});

		if ((this.xslTransformer != null) && (nodeConfig != null)) {
			try {
				body = this.xslTransformer.transform(itemToSend, nodeConfig);
			} catch (Exception e) {
				body = null;
				log.log(Level.WARNING, "Problem with generating BODY", e);
			}
		}
		for (JID jid : subscribers) {

			// in case of low memory we should slow down creation of response to
			// prevent OOM on high traffic node
			// maybe we should drop some notifications if we can not get enough
			// memory for n-th time?
			long lowMemoryDelay;
			while ((lowMemoryDelay = config.getDelayOnLowMemory()) != 0) {
				try {
					System.gc();
					Thread.sleep(lowMemoryDelay);
				} catch (Exception e) {
				}
			}

			Element message = logic.prepareNotificationMessage(jidFrom, jid, String.valueOf(++this.idCounter),
															   itemToSend, headers);
			if (body != null) {
				message.addChildren(body);
			}

			Packet packet = Packet.packetInstance(message, jidFrom, jid);

			// we are adding notifications to outgoing queue instead temporary
			// list
			// of notifications to send, so before creating next packets other
			// threads
			// will be able to process first notifications and deliver them
			packetWriter.write(packet);
		}
	}

	public void trimItems(final IItems nodeItems, final Integer maxItems, CollectionItemsOrdering collectionItemsOrdering) throws RepositoryException {
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
		eventBus.unregisterAll(this);
	}

	protected void beforePrepareNotification(final AbstractNodeConfig nodeConfig,
											 final ISubscriptions nodesSubscriptions) {
		if (nodeConfig.isPresenceExpired()) {
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

			try {
				ISubscriptions subscriptions = getRepository().getNodeSubscriptions(event.serviceJid, nodeName);
				if (subscriptions != null) {
					if (subscriptions.getSubscription(event.buddyJid.getBareJID()) == Subscription.subscribed) {
						// user is already subcribed to this node and will receive notifications event without CAPS change based delivery
						continue;
					}
				}
				publishLastItem(event.serviceJid, nodeName, event.buddyJid);
			} catch (RepositoryException ex) {
				log.log(Level.WARNING,
						"Exception while sending last published item on on_sub_and_presence for service jid " +
								event.serviceJid + " and node " + nodeName);
			}
		}

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
			BareJID serviceJid = packet.getStanzaTo().getBareJID();
			JID userJid = packet.getStanzaFrom();
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
				Logger.getLogger(PublishItemModule.class.getName()).log(Level.SEVERE, null, ex);
			}

		}

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
		final Element items = new Element("items", new String[]{"node"},
										  new String[]{publish.getAttributeStaticStr("node")});

		items.addChild(item);

		sendNotifications(subscribers, items, senderJid, null, publish.getAttributeStaticStr("node"), null);

		packetWriter.write(packet.okResult((Element) null, 0));
		sendNotifications(new JID[]{senderJid}, items, senderJid, null, publish.getAttributeStaticStr("node"), null);
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

		public final List<Element> itemsToSend;
		public final String node;
		public final String publisher;
		public final BareJID serviceJid;

		public ItemPublishedEvent(BareJID serviceJid, String node, String publisher, List<Element> itemsToSend) {
			this.serviceJid = serviceJid;
			this.node = node;
			this.publisher = publisher;
			this.itemsToSend = itemsToSend;
		}

	}
}
