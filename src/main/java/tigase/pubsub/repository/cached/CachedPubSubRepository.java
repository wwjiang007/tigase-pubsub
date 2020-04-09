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
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.ext.presence.PresenceNodeSubscriptions;
import tigase.pubsub.modules.ext.presence.PresenceNotifierModule;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.*;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.Cache;
import tigase.pubsub.utils.LRUCacheWithFuture;
import tigase.pubsub.utils.PubSubLogic;
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
@Bean(name = "repository", parent = PubSubComponent.class, active = true)
public class CachedPubSubRepository<T>
		implements IPubSubRepository, StatisticHolder, Initializable, IItems.IListnener {
	
	private final ConcurrentHashMap<BareJID, RootCollectionSet> rootCollection = new ConcurrentHashMap<>();
	@Inject
	protected IPubSubConfig config;
	@Inject
	protected IPubSubDAO<T, DataSource, Query> dao;
	protected Logger log = Logger.getLogger(this.getClass().getName());
	@Inject
	protected PubSubLogic pubSubLogic;
	protected Cache<NodeKey, Node> nodes;
	private StatisticHolder cacheStats;
	@ConfigField(desc = "Delayed load of root nodes collections", alias = "delayed-root-collection-loading")
	private boolean delayedRootCollectionLoading = false;
	private long nodes_added = 0;
	@Inject(nullAllowed = true)
	private PresenceNotifierModule presenceNotifierModule;
	private long repo_writes = 0;

	// private final Object writeThreadMutex = new Object();
	private Map<String, StatisticHolder> stats;
	private long updateSubscriptionsCalled = 0;
	private long writingTime = 0;

	protected final AtomicLong nodesCount = new AtomicLong(0);

	@Inject(nullAllowed = true)
	private IListener listener;

	@Inject(nullAllowed = true)
	private NodeAffiliationProvider<T> nodeAffiliationProvider;

	public CachedPubSubRepository() {

	}

	protected boolean isServiceAutoCreated() {
		return pubSubLogic.isServiceAutoCreated();
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Addint to root collection, serviceJid: {0}, nodeName: {1}",
					new Object[]{serviceJid, nodeName});
		}
		if (!pubSubLogic.isServiceJidPEP(serviceJid)) {
			this.getRootCollectionSet(serviceJid).add(nodeName);
		}
	}

	@Override
	public void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
						   NodeType nodeType, String collection) throws RepositoryException, PubSubException {

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"Creating node, serviceJid: {0}, nodeName: {1}, ownerJid: {2}, nodeConfig: {3}, nodeType: {4}, collection: {5}",
					new Object[]{serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collection});
		}
		pubSubLogic.checkNodeConfig(nodeConfig);

		long start = System.currentTimeMillis();
		T collectionId = null;
		if (collection != null && !collection.equals("")) {
			Node<T> collectionNode = this.getNode(serviceJid, collection);
			if (collectionNode == null) {
				throw new RepositoryException("Parent collection does not exists yet!");
			}
			collectionId = collectionNode.getNodeId();
		}

		T nodeId = this.dao.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collectionId, isServiceAutoCreated());
		if (null == nodeId) {
			throw new RepositoryException("Creating node failed!");
		}

		IAffiliationsCached nodeAffiliations = newNodeAffiliations(serviceJid, nodeName, nodeId, ()-> null);
		ISubscriptionsCached nodeSubscriptions = newNodeSubscriptions(serviceJid, nodeName, nodeId, ()-> null);
		IItems nodeItems = new Items(nodeId, serviceJid, nodeName, dao, this);
		
		Node node = new Node(nodeId, serviceJid, nodeConfig, nodeAffiliations, nodeSubscriptions, nodeItems, ownerJid, new Date());

		NodeKey key = createKey(serviceJid, nodeName);
		this.nodes.putIfAbsent(key, node);

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
	public void createService(BareJID serviceJID, boolean isPublic) throws RepositoryException {
		dao.createService(serviceJID, isPublic);
	}

	@Override
	public List<BareJID> getServices(BareJID domain, Boolean isPublic) throws RepositoryException {
		return dao.getServices(domain, isPublic);
	}

	@Override
	public void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		NodeKey key = createKey(serviceJid, nodeName);
		Node<T> node = getNode(serviceJid, nodeName);
		if (node == null) {
			throw new RepositoryException("Node does not exists!");
		}

		this.dao.deleteNode(serviceJid, node.getNodeId());

		node.setDeleted(true);

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

	protected void updateNodeConfiguration(BareJID serviceJID, String nodeName, Form config) {
		if (config != null) {
			Node node = getNodeFromCache(serviceJID, nodeName);
			if (node != null) {
				synchronized (node) {
					node.getNodeConfig().copyFromForm(config);
				}
			}
		} else {
			nodes.remove(new NodeKey(serviceJID, nodeName));
		}
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
		if (node == null) {
			return null;
		}
		return node.getNodeItems();
	}

	@Override
	public List<IItems.IItem> getNodeItems(BareJID serviceJid, String nodeName, JID requester, Date after, Date before, RSM rsm)
			throws ComponentException, RepositoryException {
		List<Node<T>> nodes = getNodeAndSubnodes(serviceJid, nodeName,
												 node -> hasAccessPermission(node, requester, PubSubLogic.Action.retrieveItems),
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

		ISubscriptionsCached xx = (node == null) ? null : node.getNodeSubscriptions();

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node subscriptions, serviceJid: {0}, nodeName: {1}, node: {2}",
					new Object[]{serviceJid, nodeName, node});
		}

		if (presenceNotifierModule == null || xx == null) {
			return xx;
		} else {
			PresencePerNodeExtension extension = presenceNotifierModule.getPresencePerNodeExtension();
			if (extension != null) {
				return new PresenceNodeSubscriptions(serviceJid, nodeName, xx, extension);
			} else {
				return xx;
			}
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
		if (pubSubLogic.isServiceJidPEP(serviceJid)) {
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
		
		affiliationsCount += nodes.values().map(Node::getNodeAffiliations).mapToInt(IAffiliations::size).sum();
		subscriptionsCount += nodes.values().map(Node::getNodeSubscriptions).mapToInt(ISubscriptions::size).sum();

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
	public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid)
			throws RepositoryException {
		if (nodeAffiliationProvider != null) {
			Map<String, UsersAffiliation> results = nodeAffiliationProvider.getUserAffiliations(serviceJid, jid);
			if (results != null) {
				return results;
			}
		}
		return this.dao.getUserAffiliations(serviceJid, jid);
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

		Cache<NodeKey, Node> cache = new LRUCacheWithFuture<>(maxCacheSize);
		cacheStats = cache;
		nodes = cache;

		// Runtime.getRuntime().addShutdownHook(makeLazyWriteThread(true));
		log.config(
				"Initializing Cached Repository with cache size = " + ((maxCacheSize == null) ? "OFF" : maxCacheSize));

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
		pubSubLogic.checkPermission(serviceJid, query.getPubsubNode(), requester, PubSubLogic.Action.retrieveItems);

		dao.queryItems(query, node.getNodeId(), itemHandler);
	}

	@Override
	public Query newQuery() {
		return new Query();
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (!pubSubLogic.isServiceJidPEP(serviceJid)) {
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
	public void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig)
			throws RepositoryException, PubSubException {
		pubSubLogic.checkNodeConfig(nodeConfig);
		Node node = getNode(serviceJid, nodeName);

		if (node != null) {
			String oldCollection = node.getNodeConfig().getCollection();
			synchronized (node) {
				node.configCopyFrom(nodeConfig);
			}

			// node.setNodeConfigChangeTimestamp();
			// synchronized (mutex) {
			log.finest("Node '" + nodeName + "' added to lazy write queue (config)");
			saveNode(node, 0);

			String newCollection = nodeConfig.getCollection();
			if (!Objects.equals(oldCollection, newCollection)) {
				nodeCollectionChanged(serviceJid, nodeName, oldCollection, newCollection);
			}
		}
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, IAffiliations nodeAffiliations) throws RepositoryException {
		if (nodeAffiliations instanceof IAffiliationsCached) {
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
				saveNode(node, 0);

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
			saveNode(node, 0);
			// }
		}
	}

	@Override
	public void deleteService(BareJID userJid) throws RepositoryException {
		dao.deleteService(userJid);
		serviceRemoved(userJid);
	}

	@Override
	public void addMAMItem(BareJID serviceJid, String nodeName, String uuid, Element message, String itemId)
			throws RepositoryException {
		Node<T> node = getNode(serviceJid, nodeName);
		if (node != null) {
			dao.addMAMItem(serviceJid, node.getNodeId(), uuid, message, itemId);
		}
	}

	@Override
	public void itemWritten(BareJID serviceJID, String node, String id, String publisher, Element item, String uuid) {
		if (listener != null) {
			listener.itemWritten(serviceJID, node, id, publisher, item, uuid);
		}
	}

	@Override
	public void itemDeleted(BareJID serviceJID, String node, String id) {
		if (listener != null) {
			listener.itemDeleted(serviceJID, node, id);
		}
	}

	protected NodeKey createKey(BareJID serviceJid, String nodeName) {
		return new NodeKey(serviceJid, nodeName);
	}

	protected Node getNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		NodeKey key = createKey(serviceJid, nodeName);
		try {
			Node<T> node = this.nodes.computeIfAbsent(key, () -> {
				try {
					return loadNode(serviceJid, nodeName);
				} catch (RepositoryException ex) {
					throw new Cache.CacheException(ex);
				}
			});
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Getting node, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}",
						new Object[]{serviceJid, nodeName, key, node});
			}
			return node;
		} catch (Cache.CacheException ex) {
			throw new RepositoryException(ex.getMessage(), ex);
		}
	}

	protected Node loadNode(BareJID serviceJid, String nodeName) throws RepositoryException {
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

		IAffiliationsCached nodeAffiliations = newNodeAffiliations(serviceJid, nodeName, nodeMeta.getNodeId(), ()-> this.dao.getNodeAffiliations(serviceJid, nodeMeta.getNodeId()));
		ISubscriptionsCached nodeSubscriptions = newNodeSubscriptions(serviceJid, nodeName, nodeMeta.getNodeId(), ()-> this.dao.getNodeSubscriptions(serviceJid, nodeMeta.getNodeId()));
		Items nodeItems = new Items(nodeMeta.getNodeId(), serviceJid, nodeName, dao, this);


		Node node = new Node(nodeMeta.getNodeId(), serviceJid, nodeConfig, nodeAffiliations, nodeSubscriptions,
						nodeItems, nodeMeta.getCreator(), nodeMeta.getCreationTime());
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"Getting node[2], serviceJid: {0}, nodeName: {1}, node: {2}, nodeAffiliations {3}, nodeSubscriptions: {4}",
					new Object[]{serviceJid, nodeName, node, nodeAffiliations, nodeSubscriptions});
		}

		return node;
	}

	protected IAffiliationsCached newNodeAffiliations(BareJID serviceJid, String nodeName, T nodeId, RepositorySupplier<Map<BareJID, UsersAffiliation>> affiliationSupplier) throws RepositoryException {
		if (nodeAffiliationProvider != null) {
			IAffiliationsCached affiliationsCached = nodeAffiliationProvider.newNodeAffiliations(serviceJid, nodeName, nodeId, affiliationSupplier);
			if (affiliationsCached != null) {
				return affiliationsCached;
			}
		}
		return new NodeAffiliations(affiliationSupplier.get());
	}

	protected ISubscriptionsCached newNodeSubscriptions(BareJID serviceJid, String nodeName, T nodeId, RepositorySupplier<Map<BareJID, UsersSubscription>> subscriptionsSupplier) throws RepositoryException {
		return new NodeSubscriptions(subscriptionsSupplier.get());
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

	protected boolean hasAccessPermission(Node node, JID requester, PubSubLogic.Action action) {
		try {
			pubSubLogic.checkPermission(node.getServiceJid(), node.getName(), requester, action);
			return true;
		} catch (PubSubException | RepositoryException ex) {
			return false;
		}
	}

	protected void serviceRemoved(BareJID userJid) {
		// clearing in memory caches
		if (listener != null) {
			listener.serviceRemoved(userJid);
		}
		try {
			nodesCount.set(dao.getNodesCount(null));
		} catch (RepositoryException ex) {
			// ignoring...
		}
		if (!pubSubLogic.isServiceJidPEP(userJid)) {
			rootCollection.remove(userJid);
		}
		boolean isPEP = pubSubLogic.isServiceJidPEP(userJid);
		NodeKey[] keys = this.nodes.keySet().toArray(new NodeKey[0]);
		for (NodeKey key : keys) {
			if (userJid.equals(key.serviceJid)) {
				this.nodes.remove(key);
			} else if (isPEP) {
				Node node = this.nodes.get(key);
				if (node != null) {
					ISubscriptionsCached nodeSubscriptions = node.getNodeSubscriptions();
					nodeSubscriptions.changeSubscription(userJid, Subscription.none);
					nodeSubscriptions.merge();
					IAffiliationsCached nodeAffiliations = node.getNodeAffiliations();
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
	
	protected void saveNode(Node<T> node, int iteration) throws RepositoryException {
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
					Node<T> collectionNode = getNode(node.getServiceJid(), collection);
					if (collectionNode == null) {
						throw new RepositoryException("Parent collection does not exists yet!");
					}
					collectionId = collectionNode.getNodeId();
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
			saveNode(node, iteration++);
		}
		// }

		long end = System.currentTimeMillis();

		writingTime += (end - start);
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
			return Objects.hash(serviceJid, node);
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
	
	public static class SizedCache<V>
			extends LinkedHashMap<NodeKey, V>
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
		public V get(Object key) {
			V val = super.get(key);
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
		protected boolean removeEldestEntry(Map.Entry<NodeKey, V> eldest) {
			return (size() > maxCacheSize);// && !eldest.getValue().needsWriting();
		}
	}

	public interface NodeAffiliationProvider<T> {

		Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid) throws RepositoryException;

		IAffiliationsCached newNodeAffiliations(BareJID serviceJid, String nodeName, T nodeId, RepositorySupplier<Map<BareJID, UsersAffiliation>> affiliationSupplier) throws RepositoryException;
	}

}
