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

import org.junit.BeforeClass;
import org.junit.Test;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.stats.StatisticsList;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xmpp.jid.BareJID;

import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CacheTest {

	private static String[][] nodes = {};

	@BeforeClass
	public static void prepareNodes() {
		Random random = new Random();
		nodes = new String[10][];
		for (int j=0; j<10; j++) {
			nodes[j] = new String[100000];
			for (int i = 0; i < 100000; i++) {
				nodes[j][i] = "node-" + String.valueOf(random.nextInt(8000));
			}
		}
	}

	@Test
	public void testLRUCache() throws Cache.CacheException {
		testCache(new LRUCache<>(2000));
	}

	@Test
	public void testLRUCacheWithFuture() throws Cache.CacheException {
		testCache(new LRUCacheWithFuture<>(2000));
	}

	private void testCache(Cache<CachedPubSubRepository.NodeKey,String> cache) throws Cache.CacheException {
		CachedPubSubRepository.NodeKey nodeKey = newNodeKey("test-1");
		assertNull(cache.get(nodeKey));
		assertEquals("test-2", cache.computeIfAbsent(nodeKey, () -> "test-2"));
		assertEquals("test-2", cache.put(nodeKey, "test-3"));
		assertEquals("test-3", cache.get(nodeKey));
		cache.remove(nodeKey);
		assertNull(cache.get(nodeKey));
	}

	private CachedPubSubRepository.NodeKey newNodeKey(String node) {
		BareJID serviceJid = BareJID.bareJIDInstanceNS("test@test.com");
		return new CachedPubSubRepository.NodeKey(serviceJid, node);
	}

	@Test
	public void testLRUCacheCompute() throws TigaseStringprepException, InterruptedException {
		for (int y = 0; y < 1; y++) {
			LRUCache<CachedPubSubRepository.NodeKey, String> cache = new LRUCache<>(2000);

			BareJID serviceJid = BareJID.bareJIDInstance("test@test.com");
			ExecutorService service = Executors.newFixedThreadPool(10);
			for (int j = 0; j < 5; j++) {
				String[] nodes = CacheTest.nodes[j];
				service.submit(() -> {
					for (int i = 0; i < 5; i++) {
						try {
							int x = i;
							CachedPubSubRepository.NodeKey key = new CachedPubSubRepository.NodeKey(serviceJid, nodes[i]);
							cache.computeIfAbsent(key, () -> {
								sleep();
								return String.valueOf(x);
							});
							cache.get(key);
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
					}
				});
			}
			service.shutdown();
			while (!service.isTerminated()) {
				service.awaitTermination(20, TimeUnit.MILLISECONDS);
			}

			cache.everyMinute();
			StatisticsList list = new StatisticsList(Level.FINEST);
			cache.getStatistics("tmp", list);
		}
	}

	@Test
	public void testLRUCacheWithFutureCompute() throws TigaseStringprepException, InterruptedException {
		for (int y = 0; y < 1; y++) {
			LRUCacheWithFuture<CachedPubSubRepository.NodeKey, String> cache = new LRUCacheWithFuture<>(2000);

			BareJID serviceJid = BareJID.bareJIDInstance("test@test.com");
			ExecutorService service = Executors.newFixedThreadPool(10);
			for (int j = 0; j < 5; j++) {
				String[] nodes = CacheTest.nodes[j];
				service.submit(() -> {
					for (int i = 0; i < 5; i++) {
						int x = i;
						CachedPubSubRepository.NodeKey key = new CachedPubSubRepository.NodeKey(serviceJid,
																								nodes[i]);
						try {
							cache.computeIfAbsent(key, () -> {
								sleep();
								return String.valueOf(x);
							});
							cache.get(key);
						} catch (Throwable e) {
							e.printStackTrace();
							assertNull("Got exception:", e);
						}
					}
				});
			}
			service.shutdown();
			while (!service.isTerminated()) {
				service.awaitTermination(20, TimeUnit.MILLISECONDS);
			}
			
			cache.everyMinute();
			StatisticsList list = new StatisticsList(Level.FINEST);
			cache.getStatistics("tmp", list);
		}
	}

	@Test
	public void testSizedCache() throws TigaseStringprepException, InterruptedException {
		for (int y = 0; y < 1; y++) {
			CachedPubSubRepository.SizedCache<String> cache = new CachedPubSubRepository.SizedCache<>(2000);
			Map<CachedPubSubRepository.NodeKey,String> map = Collections.synchronizedMap(cache);

			BareJID serviceJid = BareJID.bareJIDInstance("test@test.com");
			ExecutorService service = Executors.newFixedThreadPool(10);
			for (int j = 0; j < 5; j++) {
				String[] nodes = CacheTest.nodes[j];
				service.submit(() -> {
					for (int i = 0; i < 5; i++) {
						CachedPubSubRepository.NodeKey key = new CachedPubSubRepository.NodeKey(serviceJid,
																								nodes[i]);
						sleep();
						map.put(key, String.valueOf(i));
						map.get(key);
					}
				});
			}
			service.shutdown();
			while (!service.isTerminated()) {
				service.awaitTermination(20, TimeUnit.MILLISECONDS);
			}


			cache.everyMinute();
			StatisticsList list = new StatisticsList(Level.FINEST);
			cache.getStatistics("tmp", list);
		}
	}

	private void sleep() {
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
