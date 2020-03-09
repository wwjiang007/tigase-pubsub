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
package tigase.pubsub.repository.cached;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.ext.presence.PresenceNodeSubscriptions;
import tigase.pubsub.modules.ext.presence.PresenceNotifierModule;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.*;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.Logic;
import tigase.stats.Counter;
import tigase.stats.StatisticHolder;
import tigase.stats.StatisticHolderImpl;
import tigase.stats.StatisticsList;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.rsm.RSM;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of IPubSubRepository interface providing access to the database for data storage with caching.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:20:46 GMT
 */
@Bean(name = "pubsubRepository", parent = PubSubComponent.class, active = true)
public class CachedPubSubRepository<T>
		implements IPubSubRepository, StatisticHolder, Initializable {
	
	private final ConcurrentHashMap<BareJID, RootCollectionSet> rootCollection = new ConcurrentHashMap<>();
	@Inject
	protected PubSubConfig config;
	@Inject
	protected IPubSubDAO<T, DataSource, Query> dao;
	protected Logger log = Logger.getLogger(this.getClass().getName());
	@Inject
	protected Logic logic;
	protected Map<NodeKey, Node> nodes;
	private StatisticHolder cacheStats;
	@ConfigField(desc = "Delayed load of root nodes collections", alias = "delayed-root-collection-loading")
	private boolean delayedRootCollectionLoading = false;
	private NodeSaver nodeSaver;
	private long nodes_added = 0;
	@Inject(nullAllowed = true)
	private PresenceNotifierModule presenceNotifierModule;
	private long repo_writes = 0;

	// private final Object writeThreadMutex = new Object();
	private Map<String, StatisticHolder> stats;
	private long updateSubscriptionsCalled = 0;
	private long writingTime = 0;

	private String componentName = null;

	protected final AtomicLong nodesCount = new AtomicLong(0);

	public CachedPubSubRepository() {

	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Addint to root collection, serviceJid: {0}, nodeName: {1}",
					new Object[]{serviceJid, nodeName});
		}
		if (serviceJid.getLocalpart() == null) {
			this.getRootCollectionSet(serviceJid).add(nodeName);
		}
	}

	@Override
	public void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
						   NodeType nodeType, String collection) throws RepositoryException {

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"Creating node, serviceJid: {0}, nodeName: {1}, ownerJid: {2}, nodeConfig: {3}, nodeType: {4}, collection: {5}",
					new Object[]{serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collection});
		}
		long start = System.currentTimeMillis();
		T collectionId = null;
		if (collection != null && !collection.equals("")) {
			collectionId = this.dao.getNodeId(serviceJid, collection);
			if (collectionId == null) {
				throw new RepositoryException("Parent collection does not exists yet!");
			}
		}

		T nodeId = this.dao.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collectionId, componentName);
		if (null == nodeId) {
			nodeId = this.dao.getNodeId(serviceJid, nodeName);
			if (null == nodeId) {
				throw new RepositoryException("Creating node failed!");
			}
		}

		NodeAffiliations nodeAffiliations = tigase.pubsub.repository.NodeAffiliations.create(
				(Queue<UsersAffiliation>) null);
		NodeSubscriptions nodeSubscriptions = wrapNodeSubscriptions(
				tigase.pubsub.repository.NodeSubscriptions.create());
		Node node = new Node(nodeId, serviceJid, nodeConfig, nodeAffiliations, nodeSubscriptions, ownerJid, new Date());

		NodeKey key = createKey(serviceJid, nodeName);
		this.nodes.put(key, node);

		if (collection != null && !collection.equals("")) {
			nodeCollectionChanged(serviceJid, nodeName, null, collection);
		}

		long end = System.currentTimeMillis();

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"Creating node[2], serviceJid: {0}, nodeName: {1}, nodeAffiliations: {2}, nodeSubscriptions: {3}, node: {4}",
					new Object[]{serviceJid, nodeName, nodeAffiliations, nodeSubscriptions, node});
		}

		++nodes_added;
		nodesCount.incrementAndGet();
		writingTime += (end - start);
	}

	@Override
	public void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		NodeKey key = createKey(serviceJid, nodeName);
		Node<T> node = this.nodes.get(key);
		T nodeId = node != null ? node.getNodeId() : dao.getNodeId(serviceJid, nodeName);

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Deleting node, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}, nodeId: {4}",
					new Object[]{serviceJid, nodeName, key, node, nodeId});
		}

		this.dao.deleteNode(serviceJid, nodeId);

		if (node != null) {
			node.setDeleted(true);
		}

		this.nodes.remove(key);
		nodesCount.decrementAndGet();
	}

	@Override
	public void destroy() {

		// No resources have been allocated by the init, but some resources
		// have been allocated in the contructor....
	}

	@Override
	public void everyHour() {
		cacheStats.everyHour();

		for (StatisticHolder holder : stats.values()) {
			holder.everyHour();
		}
	}

	@Override
	public void everyMinute() {
		cacheStats.everyMinute();

		for (StatisticHolder holder : stats.values()) {
			holder.everyMinute();
		}
	}

	@Override
	public void everySecond() {
		cacheStats.everySecond();

		for (StatisticHolder holder : stats.values()) {
			holder.everySecond();
		}
	}

	@Override
	public void forgetConfiguration(BareJID serviceJid, String nodeName) throws RepositoryException {
		NodeKey key = createKey(serviceJid, nodeName);
		this.nodes.remove(key);
	}

	public Collection<Node> getAllNodes() {
		return Collections.unmodifiableCollection(nodes.values());
	}

	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException {
		Node node = this.getNode(serviceJid, nodeName);
		if (node == null) {
			return new String[0];
		}

		String[] children = node.getChildNodes();
		if (children == null) {
			children = this.dao.getChildNodes(serviceJid, nodeName);
			node.setChildNodes(Arrays.asList(children));
		}
		return children;
	}

	@Override
	public IAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		return (node == null) ? null : node.getNodeAffiliations();
	}

	@Override
	public AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node config, serviceJid: {0}, nodeName: {1}, node: {2}",
					new Object[]{serviceJid, nodeName, node});
		}
		try {
			return (node == null) ? null : node.getNodeConfig().clone();
		} catch (CloneNotSupportedException e) {
			log.log(Level.WARNING, "Error getting node config", e);
			return null;
		}
	}

	@Override
	public INodeMeta getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException {
		return getNode(serviceJid, nodeName);
	}

	@Override
	public IItems getNodeItems(BareJID serviceJid, String nodeName) throws RepositoryException {
		NodeKey key = createKey(serviceJid, nodeName);
		long start = System.currentTimeMillis();
		Node<T> node = getNode(serviceJid, nodeName);
		T nodeId = node != null ? node.getNodeId() : dao.getNodeId(serviceJid, nodeName);
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"Getting node items, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}, nodeId: {4}",
					new Object[]{serviceJid, nodeName, key, node, nodeId});
		}
		long end = System.currentTimeMillis();
		this.stats.get("getNodeItems").statisticExecutedIn(end - start);
		return nodeId != null ? new Items(nodeId, serviceJid, nodeName, this.dao) : null;
	}

	@Override
	public List<IItems.IItem> getNodeItems(BareJID serviceJid, String nodeName, JID requester, Date after, Date before, RSM rsm)
			throws ComponentException, RepositoryException {
		List<Node<T>> nodes = getNodeAndSubnodes(serviceJid, nodeName,
												 node -> hasAccessPermission(node, requester, Logic.Action.retrieveItems),
												 node -> (node.getNodeConfig() instanceof LeafNodeConfig));

		if (nodes.isEmpty()) {
			rsm.setIndex(0);
			rsm.setCount(0);
			return Collections.emptyList();
		}

		List<T> nodeIds = nodes.stream().map(node -> node.getNodeId()).collect(Collectors.toList());
		return dao.getItems(serviceJid, nodeIds, after, before, rsm, getNode(serviceJid, nodeName).getNodeConfig().getCollectionItemsOrdering());
	}

	@Override
	public ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		NodeSubscriptions xx = (node == null) ? null : node.getNodeSubscriptions();

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node subscriptions, serviceJid: {0}, nodeName: {1}, node: {2}",
					new Object[]{serviceJid, nodeName, node});
		}

		if (presenceNotifierModule == null || xx == null) {
			return xx;
		} else {
			return new PresenceNodeSubscriptions(serviceJid, nodeName, xx, presenceNotifierModule);
		}
	}

	@Override
	public long getNodesCount(BareJID serviceJID) throws RepositoryException {
		if (serviceJID != null) {
			return dao.getNodesCount(serviceJID);
		} else {
			return nodesCount.get();
		}
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return this.dao;
	}

	public void setDao(IPubSubDAO<T, DataSource, Query> dao) {
		this.dao = dao;
		try {
			nodesCount.set(dao.getNodesCount(null));
		} catch (RepositoryException ex) {
			nodesCount.set(0);
		}
	}

	@Override
	public String[] getRootCollection(BareJID serviceJid) throws RepositoryException {
		if (serviceJid.getLocalpart() != null) {
			return dao.getChildNodes(serviceJid, null);
		} else {
			RootCollectionSetIfc rootCollection = getRootCollectionSet(serviceJid);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Getting root collection, serviceJid: {0}", new Object[]{serviceJid});
			}
			if (rootCollection == null) {
				return null;
			}

			Set<String> nodes = rootCollection.values();
			return nodes.toArray(new String[nodes.size()]);
		}
	}

	@Override
	public void getStatistics(final String name, final StatisticsList stats) {
		if (this.nodes.size() > 0) {
			stats.add(name, "Cached nodes", this.nodes.size(), Level.FINE);
		} else {
			stats.add(name, "Cached nodes", this.nodes.size(), Level.FINEST);
		}

		long subscriptionsCount = 0;
		long affiliationsCount = 0;

		// synchronized (mutex) {
		Map<NodeKey, Node> tmp = null;

		synchronized (nodes) {
			tmp = new LinkedHashMap<NodeKey, Node>(nodes);
		}

		for (Node nd : tmp.values()) {
			subscriptionsCount += nd.getNodeSubscriptions().getSubscriptionsMap().size();
			affiliationsCount += nd.getNodeAffiliations().getAffiliationsMap().size();
		}

		// }

		if (updateSubscriptionsCalled > 0) {
			stats.add(name, "Update subscriptions calls", updateSubscriptionsCalled, Level.FINE);
		} else {
			stats.add(name, "Update subscriptions calls", updateSubscriptionsCalled, Level.FINEST);
		}

		if (subscriptionsCount > 0) {
			stats.add(name, "Subscriptions count (in cache)", subscriptionsCount, Level.FINE);
		} else {
			stats.add(name, "Subscriptions count (in cache)", subscriptionsCount, Level.FINEST);
		}

		if (affiliationsCount > 0) {
			stats.add(name, "Affiliations count (in cache)", affiliationsCount, Level.FINE);
		} else {
			stats.add(name, "Affiliations count (in cache)", affiliationsCount, Level.FINEST);
		}

		if (repo_writes > 0) {
			stats.add(name, "Repository writes", repo_writes, Level.FINE);
		} else {
			stats.add(name, "Repository writes", repo_writes, Level.FINEST);
		}

		if (nodes_added > 0) {
			stats.add(name, "Added new nodes", nodes_added, Level.INFO);
		} else {
			stats.add(name, "Added new nodes", nodes_added, Level.FINEST);
		}

		stats.add(name, "Total number of nodes", nodesCount.get(), Level.INFO);

		if (nodes_added > 0) {
			stats.add(name, "Total writing time", Utils.longToTime(writingTime), Level.INFO);
		} else {
			stats.add(name, "Total writing time", Utils.longToTime(writingTime), Level.FINEST);
		}

		if (nodes_added + repo_writes > 0) {
			if (nodes_added > 0) {
				stats.add(name, "Average DB write time [ms]", (writingTime / (nodes_added + repo_writes)), Level.INFO);
			} else {
				stats.add(name, "Average DB write time [ms]", (writingTime / (nodes_added + repo_writes)),
						  Level.FINEST);
			}
		}

		cacheStats.getStatistics(name, stats);

		for (StatisticHolder holder : this.stats.values()) {
			holder.getStatistics(name, stats);
		}
	}

	@Override
	public Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException {
		return this.dao.getUserRoster(owner);
	}

	@Override
	public Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID userJid)
			throws RepositoryException {
		return this.dao.getUserSubscriptions(serviceJid, userJid);
	}

	@Override
	public void init() {
		log.config("Cached PubSubRepository initialising...");
	}

	@Override
	public void initialize() {
		Integer maxCacheSize = config.getMaxCacheSize();

		final SizedCache cache = new SizedCache(maxCacheSize);
		cacheStats = cache;
		nodes = Collections.synchronizedMap(cache);

		// Runtime.getRuntime().addShutdownHook(makeLazyWriteThread(true));
		log.config(
				"Initializing Cached Repository with cache size = " + ((maxCacheSize == null) ? "OFF" : maxCacheSize));

		nodeSaver = new NodeSaver();

		this.stats = new ConcurrentHashMap<String, StatisticHolder>();
		stats.put("getNodeItems", new StatisticHolderImpl("db/getNodeItems requests"));

		// Thread.dumpStack();

	}
	
	@Override
	public void queryItems(Query query, MAMRepository.ItemHandler<Query, MAMRepository.Item> itemHandler)
			throws RepositoryException, ComponentException {

		BareJID serviceJid = query.getComponentJID().getBareJID();
		JID requester = query.getQuestionerJID();

		Node<T> node = getNode(serviceJid, query.getPubsubNode());
		if (node != null && !(node.getNodeConfig() instanceof LeafNodeConfig)) {
			throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED);
		}
		logic.checkRole(serviceJid, query.getPubsubNode(), requester, Logic.Action.retrieveItems);

		dao.queryItems(query, node.getNodeId(), itemHandler);
	}

	@Override
	public Query newQuery() {
		return new Query();
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (serviceJid.getLocalpart() == null) {
			RootCollectionSetIfc rootCollectionSet = getRootCollectionSet(serviceJid);
			if (rootCollectionSet != null) {
				rootCollectionSet.remove(nodeName);
			}
		}
	}

	@Override
	public void setStatisticsPrefix(String prefix) {
	}

	@Override
	public void statisticExecutedIn(long executionTime) {
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		if (node != null) {
			String oldCollection = node.getNodeConfig().getCollection();
			node.configCopyFrom(nodeConfig);

			// node.setNodeConfigChangeTimestamp();
			// synchronized (mutex) {
			log.finest("Node '" + nodeName + "' added to lazy write queue (config)");
			nodeSaver.save(node);

			String newCollection = nodeConfig.getCollection();
			if (!Objects.equals(oldCollection, newCollection)) {
				nodeCollectionChanged(serviceJid, nodeName, oldCollection, newCollection);
			}
		}
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, IAffiliations nodeAffiliations) throws RepositoryException {
		if (nodeAffiliations instanceof NodeAffiliations) {
			Node node = getNode(serviceJid, nodeName);

			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
						"Updating node affiliations, serviceJid: {0}, nodeName: {1}, node: {2}, nodeAffiliations: {3}",
						new Object[]{serviceJid, nodeName, node, nodeAffiliations});
			}

			if (node != null) {
				if (node.getNodeAffiliations() != nodeAffiliations) {
					throw new RuntimeException("INCORRECT");
				}

				// node.setNodeAffiliationsChangeTimestamp();
				// synchronized (mutex) {
				log.finest("Node '" + nodeName + "' added to lazy write queue (affiliations), node: " + node);
				nodeSaver.save(node);

				// }
			}
		} else {
			throw new RuntimeException("Wrong class");
		}
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, ISubscriptions nodeSubscriptions)
			throws RepositoryException {
		++updateSubscriptionsCalled;
		Node node = getNode(serviceJid, nodeName);

		if (node != null) {
			// node.setNodeSubscriptionsChangeTimestamp();
			// synchronized (mutex) {
			log.finest("Node '" + nodeName + "' added to lazy write queue (subscriptions)");
			nodeSaver.save(node);
			// }
		}
	}

	@Override
	public void onUserRemoved(BareJID userJid) throws RepositoryException {
		dao.removeService(userJid, componentName);
		userRemoved(userJid);
	}

	@Override
	public void addMAMItem(BareJID serviceJid, String nodeName, String uuid, Element message, String itemId)
			throws RepositoryException {
		Node<T> node = getNode(serviceJid, nodeName);
		T nodeId = node != null ? node.getNodeId() : dao.getNodeId(serviceJid, nodeName);
		if (nodeId != null) {
			dao.addMAMItem(serviceJid, nodeId, uuid, message, itemId);
		}
	}

	protected NodeKey createKey(BareJID serviceJid, String nodeName) {
		return new NodeKey(serviceJid, nodeName);
	}

	protected Node getNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		NodeKey key = createKey(serviceJid, nodeName);
		Node<T> node = this.nodes.get(key);

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}",
					new Object[]{serviceJid, nodeName, key, node});
		}

		if (node == null) {
			INodeMeta<T> nodeMeta = this.dao.getNodeMeta(serviceJid, nodeName);
			if (nodeMeta == null) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "Getting node[1] -- nodeId null! serviceJid: {0}, nodeName: {1}, nodeId: {2}",
							new Object[]{serviceJid, nodeName, null});
				}
				return null;
			}
			AbstractNodeConfig nodeConfig = nodeMeta.getNodeConfig();

			if (nodeConfig == null) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST,
							"Getting node[2] -- config null! serviceJid: {0}, nodeName: {1}, cfgData: {2}",
							new Object[]{serviceJid, nodeName, null});
				}
				return null;
			}

			NodeAffiliations nodeAffiliations = new NodeAffiliations(
					this.dao.getNodeAffiliations(serviceJid, nodeMeta.getNodeId()));
			NodeSubscriptions nodeSubscriptions = wrapNodeSubscriptions(
					this.dao.getNodeSubscriptions(serviceJid, nodeMeta.getNodeId()));

			node = new Node(nodeMeta.getNodeId(), serviceJid, nodeConfig, nodeAffiliations, nodeSubscriptions,
							nodeMeta.getCreator(), nodeMeta.getCreationTime());

			this.nodes.put(key, node);

			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
						"Getting node[2], serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}, nodeAffiliations {4}, nodeSubscriptions: {5}",
						new Object[]{serviceJid, nodeName, key, node, nodeAffiliations, nodeSubscriptions});
			}

		}
		return node;
	}

	protected Node getNodeFromCache(BareJID serviceJid, String nodeName) {
		NodeKey key = createKey(serviceJid, nodeName);
		return this.nodes.get(key);
	}

	protected RootCollectionSetIfc getRootCollectionSet(BareJID serviceJid) throws RepositoryException {
		RootCollectionSet rootCollection = this.rootCollection.get(serviceJid);
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting root collection, serviceJid: {0}", new Object[]{serviceJid});
		}
		if (rootCollection == null || !rootCollection.checkState(RootCollectionSet.State.initialized)) {
			if (rootCollection == null) {
				rootCollection = new RootCollectionSet(serviceJid, this);
				RootCollectionSet oldRootCollection = this.rootCollection.putIfAbsent(serviceJid, rootCollection);
				if (oldRootCollection != null) {
					rootCollection = oldRootCollection;
				}
			}

			if (!delayedRootCollectionLoading) {
				synchronized (rootCollection) {
					loadRootCollections(rootCollection);
				}
			}
		}
		return rootCollection;
	}

	protected void loadRootCollections(RootCollectionSet rootCollection) throws RepositoryException {
		BareJID serviceJid = rootCollection.getServiceJid();
		String[] x = dao.getChildNodes(serviceJid, null);
		rootCollection.loadData(x);
	}

	protected List<Node<T>> getNodeAndSubnodes(BareJID serviceJid, String nodeName,
											   Predicate<Node<T>> filterWithSubnodes, Predicate<Node<T>> filter)
			throws RepositoryException, ComponentException {
		List<Node<T>> result = new ArrayList<>();

		Node node = getNode(serviceJid, nodeName);
		if (node == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND);
		}

		if (filterWithSubnodes != null && !filterWithSubnodes.test(node)) {
			return Collections.emptyList();
		}

		if (filter != null && filter.test(node)) {
			result.add(node);
		}
		AbstractNodeConfig nodeConfig = node.getNodeConfig();
		if (nodeConfig instanceof CollectionNodeConfig) {
			String[] childNodes = getChildNodes(serviceJid, nodeName);
			if (childNodes != null) {
				for (String child : childNodes) {
					result.addAll(getNodeAndSubnodes(serviceJid, child, filterWithSubnodes, filter));
				}
			}
		}

		return result;
	}

	protected boolean hasAccessPermission(Node node, JID requester, Logic.Action action) {
		try {
			logic.checkRole(node.getServiceJid(), node.getName(), requester, action);
			return true;
		} catch (PubSubException | RepositoryException ex) {
			return false;
		}
	}

	protected void userRemoved(BareJID userJid) {
		// clearing in memory caches
		try {
			nodesCount.set(dao.getNodesCount(null));
		} catch (RepositoryException ex) {
			// ignoring...
		}
		if (userJid.getLocalpart() == null) {
			rootCollection.remove(userJid);
		}
		NodeKey[] keys = this.nodes.keySet().toArray(new NodeKey[0]);
		for (NodeKey key : keys) {
			if (userJid.equals(key.serviceJid)) {
				this.nodes.remove(key);
			} else {
				Node node = this.nodes.get(key);
				if (node != null) {
					NodeSubscriptions nodeSubscriptions = node.getNodeSubscriptions();
					nodeSubscriptions.changeSubscription(userJid, Subscription.none);
					nodeSubscriptions.merge();
					NodeAffiliations nodeAffiliations = node.getNodeAffiliations();
					nodeAffiliations.changeAffiliation(userJid, Affiliation.none);
					nodeAffiliations.merge();
				}
			}
		}
	}

	protected void nodeCollectionChanged(BareJID serviceJid, String nodeName, String oldCollection,
										 String newCollection) {
		if (oldCollection != null && !"".equals(oldCollection)) {
			Node colNode = getNodeFromCache(serviceJid, oldCollection);
			if (colNode != null) {
				colNode.childNodeRemoved(nodeName);
			}
		}
		if (newCollection != null && !"".equals(newCollection)) {
			Node colNode = getNodeFromCache(serviceJid, newCollection);
			if (colNode != null) {
				colNode.childNodeAdded(nodeName);
			}
		}
	}

	protected NodeSubscriptions wrapNodeSubscriptions(tigase.pubsub.repository.NodeSubscriptions nodeSubscriptions) {
		return new NodeSubscriptions(nodeSubscriptions);
	}

	public static class NodeKey {

		public final String node;
		public final BareJID serviceJid;

		public NodeKey(BareJID serviceJid, String node) {
			this.serviceJid = serviceJid;
			this.node = node;
		}

		@Override
		public int hashCode() {
			return serviceJid.hashCode() * 31 + node.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof NodeKey)) {
				return false;
			}
			return serviceJid.equals(((NodeKey) obj).serviceJid) && node.equals(((NodeKey) obj).node);
		}

		@Override
		public String toString() {
			return "NodeKey[serviceJid = " + serviceJid.toString() + ", node = " + node + "]";
		}
	}

	public static class RootCollectionSet implements RootCollectionSetIfc {

		private static final Logger log = Logger.getLogger(RootCollectionSet.class.getCanonicalName());

		public enum State {
			uninitialized,
			loading,
			initialized
		}
		private Set<String> added;
		private CachedPubSubRepository cachedPubSubRepository;
		private Set<String> removed;
		private Set<String> rootCollections;
		private BareJID serviceJid;
		private State state = State.uninitialized;

		public RootCollectionSet(BareJID serviceJid, CachedPubSubRepository cachedPubSubRepository) {
			this.serviceJid = serviceJid;
			this.cachedPubSubRepository = cachedPubSubRepository;
		}

		@Override
		public void add(String node) {
			synchronized (this) {
				switch (state) {
					case initialized:
						rootCollections.add(node);
						break;
					case loading:
						added.add(node);
						break;
					default:
						break;
				}
			}
		}

		public boolean checkState(State state) {
			synchronized (this) {
				return this.state == state;
			}
		}

		public BareJID getServiceJid() {
			return serviceJid;
		}

		@Override
		public void remove(String node) {
			synchronized (this) {
				switch (state) {
					case initialized:
						rootCollections.remove(node);
						break;
					case loading:
						added.remove(node);
						removed.add(node);
						break;
					default:
						break;
				}
			}
		}

		@Override
		public Set<String> values() throws IllegalStateException {
			synchronized (this) {
				switch (state) {
					case initialized:
						return rootCollections;
					case loading:
						throw new IllegalStateException(state);
					case uninitialized:
						added = new HashSet<>();
						removed = new HashSet<>();
						new Thread(this::startLoadOfData).start();
						this.state = State.loading;
						throw new IllegalStateException(state);
				}
			}
			return null;
		}

		private void startLoadOfData() {
			try {
				cachedPubSubRepository.loadRootCollections(this);
			} catch (Throwable ex) {
				log.log(Level.FINE, "Could not load");
				synchronized (this) {
					switch (state) {
						case loading:
							added = null;
							removed = null;
							state = State.uninitialized;
						default:
							break;
					}
				}
			}
		}

		private void loadData(String[] nodes) {
			synchronized (this) {
				if (nodes != null) {
					rootCollections = Collections.synchronizedSet(new HashSet<>(nodes.length));
				} else {
					rootCollections = Collections.synchronizedSet(new HashSet<>());
				}

				if (added == null && removed == null) {
					if (nodes != null) {
						rootCollections.addAll(Arrays.asList(nodes));
					}
				} else {
					rootCollections.addAll(added);
					if (nodes != null) {
						Arrays.stream(nodes).filter(node -> !removed.contains(node)).forEach(rootCollections::add);
					}
					added = null;
					removed = null;
				}
				this.state = State.initialized;
			}
		}

		public static class IllegalStateException
				extends java.lang.IllegalStateException {

			public final State state;

			public IllegalStateException(State state) {
				this.state = state;
			}

		}
	}

	private class NodeSaver {

		public void save(Node<T> node) throws RepositoryException {
			save(node, 0);
		}

		public void save(Node<T> node, int iteration) throws RepositoryException {
			long start = System.currentTimeMillis();

			++repo_writes;

			// Prevent node modifications while it is being written to DB
			// From 3.0.0 this should not be needed as we keep changes to the
			// node per thread
			// synchronized (node) {
			try {
				if (node.isDeleted()) {
					return;
				}

				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "Saving node: {0}", new Object[]{node});
				}

				if (node.configNeedsWriting()) {
					String collection = node.getNodeConfig().getCollection();
					T collectionId = null;
					if (collection != null && !collection.equals("")) {
						collectionId = dao.getNodeId(node.getServiceJid(), collection);
						if (collectionId == null) {
							throw new RepositoryException("Parent collection does not exists yet!");
						}
					}
					dao.updateNodeConfig(node.getServiceJid(), node.getNodeId(),
										 node.getNodeConfig().getFormElement().toString(), collectionId);
					node.configSaved();
				}

				if (node.affiliationsNeedsWriting()) {
					Map<BareJID, UsersAffiliation> changedAffiliations = node.getNodeAffiliations().getChanged();
					for (Map.Entry<BareJID, UsersAffiliation> entry : changedAffiliations.entrySet()) {
						dao.updateNodeAffiliation(node.getServiceJid(), node.getNodeId(), node.getName(),
												  entry.getValue());
					}
					node.affiliationsSaved();
				}

				if (node.subscriptionsNeedsWriting()) {
					// for (Integer deletedIndex :
					// fm.getRemovedFragmentIndexes()) {
					// dao.removeSubscriptions(node.getServiceJid(),
					// node.getName(), deletedIndex);
					// }
					//
					// for (Integer changedIndex :
					// fm.getChangedFragmentIndexes()) {
					// Map<BareJID, UsersSubscription> ft =
					// fm.getFragment(changedIndex);
					//
					// dao.updateSubscriptions(node.getServiceJid(),
					// node.getName(), changedIndex,
					// node.getNodeSubscriptions().serialize(ft));
					// }
					Map<BareJID, UsersSubscription> changedSubscriptions = node.getNodeSubscriptions().getChanged();
					for (Map.Entry<BareJID, UsersSubscription> entry : changedSubscriptions.entrySet()) {
						UsersSubscription subscription = entry.getValue();
						if (subscription.getSubscription() == Subscription.none) {
							dao.removeNodeSubscription(node.getServiceJid(), node.getNodeId(), subscription.getJid());
						} else {
							dao.updateNodeSubscription(node.getServiceJid(), node.getNodeId(), node.getName(),
													   subscription);
						}
					}
					node.subscriptionsSaved();
				}
			} catch (Exception e) {
				log.log(Level.WARNING, "Problem saving pubsub data: ", e);
				// if we receive an exception here, I think we should clear any
				// unsaved
				// changes (at least for affiliations and subscriptions) and
				// propagate
				// this exception to higher layer to return proper error
				// response
				//
				// should we do the same for configuration?
				node.resetChanges();
				throw new RepositoryException("Problem saving pubsub data", e);
			}

			// If the node still needs writing to the database put
			// it back to the collection
			if (node.needsWriting()) {
				if (iteration >= 10) {
					String msg =
							"Was not able to save data for node " + node.getName() + " on " + iteration + " iteration" +
									", config saved = " + (!node.configNeedsWriting()) + ", affiliations saved = " +
									(!node.affiliationsNeedsWriting()) + ", subscriptions saved = " +
									(!node.subscriptionsNeedsWriting());
					log.log(Level.WARNING, msg);
					throw new RepositoryException("Problem saving pubsub data");
				}
				save(node, iteration++);
			}
			// }

			long end = System.currentTimeMillis();

			writingTime += (end - start);
		}
	}

	private class SizedCache
			extends LinkedHashMap<NodeKey, Node>
			implements StatisticHolder {

		private static final long serialVersionUID = 1L;

		private Counter hitsCounter = new Counter("cache/hits", Level.FINEST);

		private int maxCacheSize = 1000;
		private Counter requestsCounter = new Counter("cache/requests", Level.FINEST);

		public SizedCache(int maxSize) {
			super(maxSize, 0.1f, true);
			maxCacheSize = maxSize;
		}

		@Override
		public void everyHour() {
			requestsCounter.everyHour();
			hitsCounter.everyHour();
		}

		@Override
		public void everyMinute() {
			requestsCounter.everyMinute();
			hitsCounter.everyMinute();
		}

		@Override
		public void everySecond() {
			requestsCounter.everySecond();
			hitsCounter.everySecond();
		}

		@Override
		public Node get(Object key) {
			Node val = super.get(key);
			requestsCounter.inc();
			if (val != null) {
				hitsCounter.inc();
			}
			return val;
		}

		@Override
		public void getStatistics(String compName, StatisticsList list) {
			requestsCounter.getStatistics(compName, list);
			hitsCounter.getStatistics(compName, list);
			list.add(compName, "cache/hit-miss ratio per minute", (requestsCounter.getPerMinute() == 0)
																  ? 0
																  : ((float) hitsCounter.getPerMinute()) /
																		  requestsCounter.getPerMinute(), Level.FINE);
			list.add(compName, "cache/hit-miss ratio per second", (requestsCounter.getPerSecond() == 0)
																  ? 0
																  : ((float) hitsCounter.getPerSecond()) /
																		  requestsCounter.getPerSecond(), Level.FINE);
		}

		@Override
		public void setStatisticsPrefix(String prefix) {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public void statisticExecutedIn(long executionTime) {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<NodeKey, Node> eldest) {
			return (size() > maxCacheSize) && !eldest.getValue().needsWriting();
		}
	}

}
