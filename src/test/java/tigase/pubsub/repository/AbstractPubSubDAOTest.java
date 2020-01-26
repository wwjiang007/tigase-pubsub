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
package tigase.pubsub.repository;

import org.junit.*;
import org.junit.runners.MethodSorters;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.AbstractDataSourceAwareTestCase;
import tigase.db.DBInitException;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.db.xml.XMLRepository;
import tigase.kernel.core.Kernel;
import tigase.pubsub.*;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by andrzej on 12.10.2016.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractPubSubDAOTest<DS extends DataSource> extends AbstractDataSourceAwareTestCase<DS, IPubSubDAO> {

	protected static String emoji = "\uD83D\uDE97\uD83D\uDCA9\uD83D\uDE21";
	protected boolean checkEmoji = true;
	protected IPubSubDAO dao;
	private String nodeNameWithoutEmoji = "test-node";
	private String nodeName = nodeNameWithoutEmoji;
	private JID senderJid = JID.jidInstanceNS("owner@tigase/tigase-1");
	private BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub.tigase");
	private JID subscriberJid = JID.jidInstanceNS("subscriber@tigase/tigase-1");

	@Before
	public void setup() throws RepositoryException, DBInitException, IllegalAccessException, InstantiationException {
		if (checkEmoji) {
			nodeName += emoji;
		}
		dao = getDataSourceAware();
	}

	@After
	public void tearDown() {
		if (dao != null) {
			dao.destroy();
		}
		dao = null;
	}

	@Test
	public void test00_testNodesCount() throws RepositoryException {
		long value = dao.getNodesCount(null);
		assertTrue(value >= 0);
	}

	@Test
	public void test01_createNode() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		if (node != null) {
			dao.deleteNode(serviceJid, node.getNodeId());
		}

		LeafNodeConfig nodeCfg = new LeafNodeConfig(nodeName);
		dao.createNode(serviceJid, nodeName, senderJid.getBareJID(), nodeCfg, NodeType.leaf, null, "pubsub");

		node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not retrieve nodeId for newly created node", node);
	}

	@Test()
	public void test02_subscribeNode() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);
		UsersSubscription subscr = new UsersSubscription(subscriberJid.getBareJID(), "sub-1", Subscription.subscribed);
		dao.updateNodeSubscription(serviceJid, node.getNodeId(), nodeName, subscr);

		Map<BareJID, UsersSubscription> nodeSubscr = dao.getNodeSubscriptions(serviceJid, node.getNodeId());
		Assert.assertNotNull("Not found subscriptions for node", nodeSubscr);
		UsersSubscription usersSubscription = nodeSubscr.get(subscriberJid.getBareJID());
		assertNotNull(usersSubscription);
		Assert.assertEquals("Bad subscription type for user", Subscription.subscribed, usersSubscription.getSubscription());
	}

	@Test
	public void test03_affiliateNode() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);
		UsersAffiliation affil = new UsersAffiliation(subscriberJid.getBareJID(), Affiliation.publisher);
		dao.updateNodeAffiliation(serviceJid, node.getNodeId(), nodeName, affil);

		Map<BareJID, UsersAffiliation> nodeAffils = dao.getNodeAffiliations(serviceJid, node.getNodeId());
		Assert.assertNotNull("Not found affiliations for node", nodeAffils);
		affil = nodeAffils.get(subscriberJid.getBareJID());
		Assert.assertNotNull("Not found affiliation for user", affil);
		Affiliation affiliation = affil.getAffiliation();
		Assert.assertEquals("Bad affiliation type for user", Affiliation.publisher, affiliation);
	}

	@Test
	public void test04_userSubscriptions() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);
		Map<String, UsersSubscription> map = dao.getUserSubscriptions(serviceJid, subscriberJid.getBareJID());
		Assert.assertNotNull("No subscriptions for user", map);
		UsersSubscription subscr = map.get(nodeName);
		Assert.assertNotNull("No subscription for user for node", subscr);
		Assert.assertEquals("Bad subscription for user for node", Subscription.subscribed, subscr.getSubscription());
	}

	@Test
	public void test05_userAffiliations() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);
		Map<String, UsersAffiliation> map = dao.getUserAffiliations(serviceJid, subscriberJid.getBareJID());
		Assert.assertNotNull("No affiliation for user", map);
		UsersAffiliation affil = map.get(nodeName);
		Assert.assertNotNull("No affiliation for user for node", affil);
		Assert.assertEquals("Bad affiliation for user for node", Affiliation.publisher, affil.getAffiliation());
	}

	@Test
	public void test06_allNodes() throws RepositoryException {
		String[] allNodes = dao.getAllNodesList(serviceJid);
		Arrays.sort(allNodes);
		Assert.assertNotEquals("Node name not listed in list of all root nodes", -1,
							   Arrays.binarySearch(allNodes, nodeName));
	}

	@Test
	public void test06_getNodeMeta() throws RepositoryException {
		INodeMeta meta = dao.getNodeMeta(serviceJid, nodeName);
		assertNotNull(meta);
		Object nodeId = dao.getNodeMeta(serviceJid, nodeName);
		assertEquals(nodeId, meta.getNodeId());
		assertEquals(nodeName, meta.getNodeConfig().getNodeName());
		assertEquals(senderJid.getBareJID(), meta.getCreator());
		assertNotNull(meta.getCreationTime());
	}

	@Test
	public void test07_nodeItems() throws RepositoryException {
		String itemId = "item-1";
		Element item = new Element("item", new String[]{"id"}, new String[]{itemId});
		String payloadCData = "test-payload";
		if (checkEmoji) {
			payloadCData += emoji;
		}
		item.addChild(new Element("payload", payloadCData, new String[]{"xmlns"}, new String[]{"test-xmlns"}));

		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);
		dao.writeItem(serviceJid, node.getNodeId(), System.currentTimeMillis(), itemId, nodeNameWithoutEmoji, item, null);

		String[] itemsIds = dao.getItemsIds(serviceJid, node.getNodeId(), CollectionItemsOrdering.byUpdateDate);
		Assert.assertArrayEquals("Added item id not listed in list of item ids", new String[]{itemId}, itemsIds);

		Element el = Optional.ofNullable(dao.getItem(serviceJid, node.getNodeId(), itemId)).map(IItems.IItem::getItem).orElse(null);
		Assert.assertEquals("Element retrieved from store do not match to element added to store", item, el);

		dao.deleteItem(serviceJid, node.getNodeId(), itemId);
		el = Optional.ofNullable(dao.getItem(serviceJid, node.getNodeId(), itemId)).map(IItems.IItem::getItem).orElse(null);
		assertNull("Element still available in store after removal", el);
	}
	
	@Test
	public void test09_subscribeNodeRemoval() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);
		UsersSubscription subscr = new UsersSubscription(subscriberJid.getBareJID(), "sub-1", Subscription.none);
		dao.updateNodeSubscription(serviceJid, node.getNodeId(), nodeName, subscr);

		Map<BareJID, UsersSubscription> nodeSubscr = dao.getNodeSubscriptions(serviceJid, node.getNodeId());
		Assert.assertNotNull("Not found subscriptions for node", nodeSubscr);
		UsersSubscription subscription = nodeSubscr.get(subscriberJid.getBareJID());
		Assert.assertEquals("Bad subscription type for user", Subscription.none, subscription.getSubscription());
	}

	@Test
	public void test09_affiliateNodeRemoval() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);
		UsersAffiliation affil = new UsersAffiliation(subscriberJid.getBareJID(), Affiliation.none);
		dao.updateNodeAffiliation(serviceJid, node.getNodeId(), nodeName, affil);

		Map<BareJID, UsersAffiliation> nodeAffils = dao.getNodeAffiliations(serviceJid, node.getNodeId());
		Assert.assertNotNull("Not found affiliations for node", nodeAffils);
		affil = nodeAffils.get(subscriberJid.getBareJID());
		Assert.assertEquals("Bad affiliation for user", Affiliation.none, affil.getAffiliation());
	}

	@Test
	public void test10_nodeRemoval() throws RepositoryException {
		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		dao.deleteNode(serviceJid, node.getNodeId());
		node = dao.getNodeMeta(serviceJid, nodeName);
		assertNull("Node not removed", node);
	}

	@Override
	protected Class<? extends DataSourceAware> getDataSourceAwareIfc() {
		return IPubSubDAO.class;
	}

	@Test
	public void test08_queryNodeItems()
			throws RepositoryException, InterruptedException, ComponentException {
		List<Element> publishedItems = new ArrayList<>();

		INodeMeta node = dao.getNodeMeta(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", node);

		Map<String,String> uuids = new HashMap<>();

		for (int i = 0; i < 20; i++) {
			String itemId = "item-" + i;
			String payloadCData = "test-payload";
			if (checkEmoji) {
				payloadCData += emoji;
				itemId += emoji;
			}

			Element item = new Element("item", new String[]{"id"}, new String[]{itemId});
			item.addChild(
					new Element("payload", payloadCData + "-" + i, new String[]{"xmlns"}, new String[]{"test-xmlns"}));

			String uuid = UUID.randomUUID().toString().toLowerCase();
			dao.writeItem(serviceJid, node.getNodeId(), System.currentTimeMillis(), itemId, senderJid.getBareJID().toString(),
						  item, uuid);
			dao.addMAMItem(serviceJid, node.getNodeId(), uuid, item, itemId);
			uuids.put(itemId, uuid);

			publishedItems.add(item);

			Thread.sleep(1500);
		}

		String[] publishedItemIds = publishedItems.stream()
				.map(el -> el.getAttributeStaticStr("id"))
				.toArray(value -> new String[value]);

		List<IPubSubRepository.Item> results = new ArrayList<>();

		Query query = new Query();
		query.setComponentJID(JID.jidInstance(serviceJid));
		query.setQuestionerJID(senderJid);
		query.getRsm().setMax(10);
		results.clear();
		dao.queryItems(query, node.getNodeId(), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(uuids.get(publishedItemIds[i]), item.getId());
			assertEquals(publishedItems.get(i), item.getMessage());
		}

		query.setWith(senderJid.copyWithoutResource());
		results.clear();
		dao.queryItems(query, node.getNodeId(), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(uuids.get(publishedItemIds[i]), item.getId());
			assertEquals(publishedItems.get(i), item.getMessage());
		}
		query.setWith(null);

		String after = results.get(4).getId();
		query.getRsm().setAfter(after);
		results.clear();
		dao.queryItems(query, node.getNodeId(), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(uuids.get(publishedItemIds[i + 5]), item.getId());
			assertEquals(publishedItems.get(i + 5), item.getMessage());
		}

		query.getRsm().setAfter(null);
		query.getRsm().setHasBefore(true);
		results.clear();
		dao.queryItems(query, node.getNodeId(), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(uuids.get(publishedItemIds[i + 10]), item.getId());
			assertEquals(publishedItems.get(i + 10), item.getMessage());
		}

		String[] itemsIds = dao.getItemsIds(serviceJid, node.getNodeId(), CollectionItemsOrdering.byUpdateDate);
		Arrays.sort(itemsIds);
		String[] tmp = Arrays.copyOf(publishedItemIds, publishedItemIds.length);
		Arrays.sort(tmp);
		Assert.assertArrayEquals("Added item id not listed in list of item ids", tmp, itemsIds);

		for (String itemId : publishedItemIds) {
			dao.deleteItem(serviceJid, node.getNodeId(), itemId);
			Element el = Optional.ofNullable(dao.getItem(serviceJid, node.getNodeId(), itemId)).map(
					IItems.IItem::getItem).orElse(null);
			assertNull("Element still available in store after removal", el);
		}
	}

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		try {
			String xmlRepositoryURI = "memory://xmlRepo?autoCreateUser=true";
			XMLRepository repository = new XMLRepository();
			repository.initRepository(xmlRepositoryURI, null);
			kernel.registerBean("userAuthRepository").asInstance(repository).exportable().exec();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to initialize user/auth repository", ex);
		}
	}

}
