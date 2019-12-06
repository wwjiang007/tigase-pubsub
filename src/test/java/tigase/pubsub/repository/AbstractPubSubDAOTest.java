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
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		if (nodeId != null) {
			dao.deleteNode(serviceJid, nodeId);
		}

		LeafNodeConfig nodeCfg = new LeafNodeConfig(nodeName);
		dao.createNode(serviceJid, nodeName, senderJid.getBareJID(), nodeCfg, NodeType.leaf, null);

		nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not retrieve nodeId for newly created node", nodeId);
	}

	@Test()
	public void test02_subscribeNode() throws RepositoryException {
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);
		UsersSubscription subscr = new UsersSubscription(subscriberJid.getBareJID(), "sub-1", Subscription.subscribed);
		dao.updateNodeSubscription(serviceJid, nodeId, nodeName, subscr);

		NodeSubscriptions nodeSubscr = dao.getNodeSubscriptions(serviceJid, nodeId);
		Assert.assertNotNull("Not found subscriptions for node", nodeSubscr);
		Subscription subscription = nodeSubscr.getSubscription(subscriberJid.getBareJID());
		Assert.assertEquals("Bad subscription type for user", Subscription.subscribed, subscription);
	}

	@Test
	public void test03_affiliateNode() throws RepositoryException {
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);
		UsersAffiliation affil = new UsersAffiliation(subscriberJid.getBareJID(), Affiliation.publisher);
		dao.updateNodeAffiliation(serviceJid, nodeId, nodeName, affil);

		NodeAffiliations nodeAffils = dao.getNodeAffiliations(serviceJid, nodeId);
		Assert.assertNotNull("Not found affiliations for node", nodeAffils);
		affil = nodeAffils.getSubscriberAffiliation(subscriberJid.getBareJID());
		Assert.assertNotNull("Not found affiliation for user", affil);
		Affiliation affiliation = affil.getAffiliation();
		Assert.assertEquals("Bad affiliation type for user", Affiliation.publisher, affiliation);
	}

	@Test
	public void test04_userSubscriptions() throws RepositoryException {
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);
		Map<String, UsersSubscription> map = dao.getUserSubscriptions(serviceJid, subscriberJid.getBareJID());
		Assert.assertNotNull("No subscriptions for user", map);
		UsersSubscription subscr = map.get(nodeName);
		Assert.assertNotNull("No subscription for user for node", subscr);
		Assert.assertEquals("Bad subscription for user for node", Subscription.subscribed, subscr.getSubscription());
	}

	@Test
	public void test05_userAffiliations() throws RepositoryException {
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);
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
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
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

		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);
		dao.writeItem(serviceJid, nodeId, System.currentTimeMillis(), itemId, nodeNameWithoutEmoji, item);

		String[] itemsIds = dao.getItemsIds(serviceJid, nodeId);
		Assert.assertArrayEquals("Added item id not listed in list of item ids", new String[]{itemId}, itemsIds);

		Element el = dao.getItem(serviceJid, nodeId, itemId);
		Assert.assertEquals("Element retrieved from store do not match to element added to store", item, el);

		dao.deleteItem(serviceJid, nodeId, itemId);
		el = dao.getItem(serviceJid, nodeId, itemId);
		assertNull("Element still available in store after removal", el);
	}

	@Test
	public void test08_queryNodeItems() throws RepositoryException, InterruptedException, ComponentException {
		test08_queryNodeItems(CollectionItemsOrdering.byCreationDate);
		test08_queryNodeItems(CollectionItemsOrdering.byUpdateDate);
	}

	@Test
	public void test09_subscribeNodeRemoval() throws RepositoryException {
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);
		UsersSubscription subscr = new UsersSubscription(subscriberJid.getBareJID(), "sub-1", Subscription.none);
		dao.updateNodeSubscription(serviceJid, nodeId, nodeName, subscr);

		NodeSubscriptions nodeSubscr = dao.getNodeSubscriptions(serviceJid, nodeId);
		Assert.assertNotNull("Not found subscriptions for node", nodeSubscr);
		Subscription subscription = nodeSubscr.getSubscription(subscriberJid.getBareJID());
		Assert.assertEquals("Bad subscription type for user", Subscription.none, subscription);
	}

	@Test
	public void test09_affiliateNodeRemoval() throws RepositoryException {
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);
		UsersAffiliation affil = new UsersAffiliation(subscriberJid.getBareJID(), Affiliation.none);
		dao.updateNodeAffiliation(serviceJid, nodeId, nodeName, affil);

		NodeAffiliations nodeAffils = dao.getNodeAffiliations(serviceJid, nodeId);
		Assert.assertNotNull("Not found affiliations for node", nodeAffils);
		affil = nodeAffils.getSubscriberAffiliation(subscriberJid.getBareJID());
		Assert.assertEquals("Bad affiliation for user", Affiliation.none, affil.getAffiliation());
	}

	@Test
	public void test10_nodeRemoval() throws RepositoryException {
		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		dao.deleteNode(serviceJid, nodeId);
		nodeId = dao.getNodeId(serviceJid, nodeName);
		assertNull("Node not removed", nodeId);
	}

	@Override
	protected Class<? extends DataSourceAware> getDataSourceAwareIfc() {
		return IPubSubDAO.class;
	}

	protected void test08_queryNodeItems(CollectionItemsOrdering order)
			throws RepositoryException, InterruptedException, ComponentException {
		List<Element> publishedItems = new ArrayList<>();

		Object nodeId = dao.getNodeId(serviceJid, nodeName);
		Assert.assertNotNull("Could not fined nodeId", nodeId);

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

			dao.writeItem(serviceJid, nodeId, System.currentTimeMillis(), itemId, senderJid.getBareJID().toString(),
						  item);

			publishedItems.add(item);

			Thread.sleep(1500);
		}

		String[] publishedItemIds = publishedItems.stream()
				.map(el -> el.getAttributeStaticStr("id"))
				.toArray(value -> new String[value]);

		List<IPubSubRepository.Item> results = new ArrayList<>();

		Query query = new Query();
		query.setOrder(order);
		query.setComponentJID(JID.jidInstance(serviceJid));
		query.setQuestionerJID(senderJid);
		query.getRsm().setMax(10);
		results.clear();
		dao.queryItems(query, Collections.singletonList(nodeId), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(getMAMID(nodeId, publishedItemIds[i]), item.getId());
			assertEquals(publishedItems.get(i), item.getMessage());
		}

		query.setWith(senderJid.copyWithoutResource());
		results.clear();
		dao.queryItems(query, Collections.singletonList(nodeId), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(getMAMID(nodeId, publishedItemIds[i]), item.getId());
			assertEquals(publishedItems.get(i), item.getMessage());
		}
		query.setWith(null);

		String after = results.get(4).getId();
		query.getRsm().setAfter(after);
		results.clear();
		dao.queryItems(query, Collections.singletonList(nodeId), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(getMAMID(nodeId, publishedItemIds[i + 5]), item.getId());
			assertEquals(publishedItems.get(i + 5), item.getMessage());
		}

		query.getRsm().setAfter(null);
		query.getRsm().setHasBefore(true);
		results.clear();
		dao.queryItems(query, Collections.singletonList(nodeId), (query1, item) -> {
			results.add((IPubSubRepository.Item) item);
		});

		assertEquals(10, results.size());
		for (int i = 0; i < 10; i++) {
			IPubSubRepository.Item item = results.get(i);
			assertEquals(getMAMID(nodeId, publishedItemIds[i + 10]), item.getId());
			assertEquals(publishedItems.get(i + 10), item.getMessage());
		}

		String[] itemsIds = dao.getItemsIds(serviceJid, nodeId);
		Arrays.sort(itemsIds);
		String[] tmp = Arrays.copyOf(publishedItemIds, publishedItemIds.length);
		Arrays.sort(tmp);
		Assert.assertArrayEquals("Added item id not listed in list of item ids", tmp, itemsIds);

		for (String itemId : publishedItemIds) {
			dao.deleteItem(serviceJid, nodeId, itemId);
			Element el = dao.getItem(serviceJid, nodeId, itemId);
			assertNull("Element still available in store after removal", el);
		}
	}

	protected abstract String getMAMID(Object nodeId, String itemId);

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
