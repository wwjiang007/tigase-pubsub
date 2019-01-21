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
package tigase.pubsub.repository.cached;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.kernel.core.Kernel;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.*;
import tigase.pubsub.repository.NodeAffiliations;
import tigase.pubsub.repository.NodeSubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.Logic;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Created by andrzej on 26.01.2017.
 */
public class CachedPubSubRepositoryTest {

	private Kernel kernel;

	@Before
	public void setUp() {
		kernel = new Kernel();
	}

	@After
	public void tearDown() {
		kernel = null;
	}

	@Test
	public void test_lazyLoadingOfRootCollections() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);
		setDelayedRootCollectionLoading(cachedPubSubRepository, true);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i = 0; i < 10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		try {
			String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
			assertFalse(true);
		} catch (CachedPubSubRepository.RootCollectionSet.IllegalStateException ex) {
			assertTrue(true);
		}

		for (int i = 0; i < 2; i++) {
			dao.rootCollections.get(serviceJid).remove(nodes[i]);
			cachedPubSubRepository.removeFromRootCollection(serviceJid, nodes[i]);
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			cachedPubSubRepository.addToRootCollection(serviceJid, node);
		}
		Arrays.sort(nodes);

		Thread.sleep(1000);

		String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
		Arrays.sort(result);

		assertArrayEquals(nodes, result);
	}

	@Test
	public void test_eagerLoadingOfRootCollections() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i = 0; i < 10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		Arrays.sort(nodes);

		new Thread(() -> {
			try {
				String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
				assertArrayEquals(nodes, result);
			} catch (Exception ex) {
				assertFalse(true);
			}
		});

		String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
		Arrays.sort(result);

		assertArrayEquals(nodes, result);
	}

	@Test
	public void test_userRemoved_lazy() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);
		setDelayedRootCollectionLoading(cachedPubSubRepository, true);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i = 0; i < 10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		try {
			cachedPubSubRepository.getRootCollection(serviceJid);
		} catch (Exception ex) {
		}
		Thread.sleep(1000);
		assertEquals(10, cachedPubSubRepository.getRootCollection(serviceJid).length);

		cachedPubSubRepository.onUserRemoved(serviceJid);

		try {
			cachedPubSubRepository.getRootCollection(serviceJid);
		} catch (Exception ex) {
		}
		Thread.sleep(1000);
		assertEquals(0, cachedPubSubRepository.getRootCollection(serviceJid).length);
		assertNull(dao.getChildNodes(serviceJid, null));
	}

	@Test
	public void test_userRemoved_eager() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i = 0; i < 10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		assertEquals(10, cachedPubSubRepository.getRootCollection(serviceJid).length);

		cachedPubSubRepository.onUserRemoved(serviceJid);

		assertEquals(0, cachedPubSubRepository.getRootCollection(serviceJid).length);
		assertNull(dao.getChildNodes(serviceJid, null));
	}

	protected CachedPubSubRepository createCachedPubSubRepository(PubSubDAO dao) {
		kernel.registerBean("pubsubDao").asInstance(dao).exec();
		kernel.registerBean("logic").asInstance(new Logic() {

			@Override
			public void checkAccessPermission(BareJID serviceJid, String nodeName, JID senderJid)
					throws PubSubException, RepositoryException {

			}

			@Override
			public void checkAccessPermission(BareJID serviceJid, AbstractNodeConfig nodeConfig,
											  IAffiliations nodeAffiliations, ISubscriptions nodeSubscriptions,
											  JID senderJid) throws PubSubException, RepositoryException {

			}

			@Override
			public boolean hasSenderSubscription(BareJID bareJid, IAffiliations affiliations,
												 ISubscriptions subscriptions) throws RepositoryException {
				return false;
			}

			@Override
			public boolean isSenderInRosterGroup(BareJID bareJid, AbstractNodeConfig nodeConfig,
												 IAffiliations affiliations, ISubscriptions subscriptions)
					throws RepositoryException {
				return false;
			}

			@Override
			public Element prepareNotificationMessage(JID from, JID to, String id, Element itemToSend,
													  Map<String, String> headers) {
				return null;
			}
		}).exec();
		kernel.registerBean("config").asInstance(new PubSubConfig()).exec();
		kernel.registerBean("cachedRepository").asClass(CachedPubSubRepository.class).exec();
		return kernel.getInstance(CachedPubSubRepository.class);
	}

	protected void setDelayedRootCollectionLoading(CachedPubSubRepository cachedPubSubRepository, boolean value)
			throws NoSuchFieldException, IllegalAccessException {
		Field f = CachedPubSubRepository.class.getDeclaredField("delayedRootCollectionLoading");
		f.setAccessible(true);
		f.set(cachedPubSubRepository, value);
	}

	public static class DummyPubSubDAO
			extends PubSubDAO {

		protected Map<BareJID, Set<String>> rootCollections = new ConcurrentHashMap<>();
		protected boolean withDelay;

		@Override
		public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
			synchronized (rootCollections) {
				Set<String> nodes = rootCollections.computeIfAbsent(serviceJid, bareJID -> new HashSet<String>());
				nodes.add(nodeName);
			}
		}

		@Override
		public Object createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
								 NodeType nodeType, Object collectionId) throws RepositoryException {
			return null;
		}

		@Override
		public void deleteItem(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {

		}

		@Override
		public void deleteNode(BareJID serviceJid, Object nodeId) throws RepositoryException {

		}

		@Override
		public String[] getAllNodesList(BareJID serviceJid) throws RepositoryException {
			return new String[0];
		}

		@Override
		public Element getItem(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {
			return null;
		}

		@Override
		public Date getItemCreationDate(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {
			return null;
		}

		@Override
		public String[] getItemsIds(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return new String[0];
		}

		@Override
		public String[] getItemsIdsSince(BareJID serviceJid, Object nodeId, Date since) throws RepositoryException {
			return new String[0];
		}

		@Override
		public List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, Object nodeId, String nodeName)
				throws RepositoryException {
			return null;
		}

		@Override
		public Date getItemUpdateDate(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {
			return null;
		}

		@Override
		public NodeAffiliations getNodeAffiliations(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return null;
		}

		@Override
		public String getNodeConfig(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return null;
		}

		@Override
		public Object getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException {
			return null;
		}

		@Override
		public INodeMeta getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException {
			return null;
		}

		@Override
		public long getNodesCount(BareJID serviceJid) throws RepositoryException {
			return 0;
		}

		@Override
		public String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException {
			return new String[0];
		}

		@Override
		public NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return null;
		}

		@Override
		public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException {
			Set<String> nodes = rootCollections.get(serviceJid);
			sleep();
			return nodes == null ? null : nodes.toArray(new String[nodes.size()]);
		}

		@Override
		public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid)
				throws RepositoryException {
			return null;
		}

		@Override
		public Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid)
				throws RepositoryException {
			return null;
		}

		@Override
		public void queryItems(Query query, List nodesIds, MAMRepository.ItemHandler itemHandler)
				throws RepositoryException, ComponentException {

		}

		@Override
		public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException {

		}

		@Override
		public void removeService(BareJID serviceJid) throws RepositoryException {
			rootCollections.remove(serviceJid);
		}

		@Override
		public void removeFromRootCollection(BareJID serviceJid, Object nodeId) throws RepositoryException {
		}

		@Override
		public void removeNodeSubscription(BareJID serviceJid, Object nodeId, BareJID jid) throws RepositoryException {

		}

		@Override
		public void updateNodeConfig(BareJID serviceJid, Object nodeId, String serializedData, Object collectionId)
				throws RepositoryException {

		}

		@Override
		public void updateNodeAffiliation(BareJID serviceJid, Object nodeId, String nodeName,
										  UsersAffiliation userAffiliation) throws RepositoryException {

		}

		@Override
		public void updateNodeSubscription(BareJID serviceJid, Object nodeId, String nodeName,
										   UsersSubscription userSubscription) throws RepositoryException {

		}

		@Override
		public void writeItem(BareJID serviceJid, Object nodeId, long timeInMilis, String id, String publisher,
							  Element item) throws RepositoryException {

		}

		@Override
		public void setDataSource(DataSource dataSource) {

		}

		protected void sleep() {
			if (!withDelay) {
				return;
			}

			try {
				Thread.sleep(400);
			} catch (InterruptedException ex) {
				assertFalse(true);
			}
		}
	}
}
