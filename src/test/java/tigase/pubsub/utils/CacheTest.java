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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
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

@Ignore
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
	public void testLRUCache() throws TigaseStringprepException, InterruptedException {
		long avgTime = 0;
		for (int y = 0; y < 1; y++) {
			System.gc();
			LRUCache<CachedPubSubRepository.NodeKey, String> cache = new LRUCache<>(2000);

			BareJID serviceJid = BareJID.bareJIDInstance("test@test.com");
			long start = System.currentTimeMillis();
			ExecutorService service = Executors.newFixedThreadPool(10);
			for (int j = 0; j < 10; j++) {
				String[] nodes = CacheTest.nodes[j];
				service.submit(() -> {
					for (int i = 0; i < 100000; i++) {
						try {
							CachedPubSubRepository.NodeKey key = new CachedPubSubRepository.NodeKey(serviceJid, nodes[i]);
							sleep();
							cache.putIfAbsent(key, String.valueOf(i));
							cache.get(key);
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
					}
				});
			}
			service.shutdown();
			while (!service.isTerminated()) {
				service.awaitTermination(10, TimeUnit.MILLISECONDS);
			}

			long end = System.currentTimeMillis();
			System.out.println("lru done in:" + (end - start) + "ms, size:" + cache.size());

			cache.everyMinute();
			StatisticsList list = new StatisticsList(Level.FINEST);
			cache.getStatistics("tmp", list);
			System.out.println(list.toString());
			avgTime += (end - start);
		}
		System.out.println("avg lru done in:" + (avgTime / 3) + "ms");
	}

	@Test
	public void testLRUCacheWithFuture() throws TigaseStringprepException, InterruptedException {
		long avgTime = 0;
		for (int y = 0; y < 1; y++) {
			System.gc();
			LRUCacheWithFuture<CachedPubSubRepository.NodeKey, String> cache = new LRUCacheWithFuture<>(2000);

			BareJID serviceJid = BareJID.bareJIDInstance("test@test.com");
			long start = System.currentTimeMillis();
			ExecutorService service = Executors.newFixedThreadPool(10);
			for (int j = 0; j < 10; j++) {
				String[] nodes = CacheTest.nodes[j];
				service.submit(() -> {
					for (int i = 0; i < 100000; i++) {
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
							Assert.assertNull("Got exception:", e);
						}
					}
				});
			}
			service.shutdown();
			while (!service.isTerminated()) {
				service.awaitTermination(10, TimeUnit.MILLISECONDS);
			}

			long end = System.currentTimeMillis();
			System.out.println("lru future done in:" + (end - start) + "ms, size:" + cache.size());

			cache.everyMinute();
			StatisticsList list = new StatisticsList(Level.FINEST);
			cache.getStatistics("tmp", list);
			System.out.println(list.toString());
			avgTime += (end - start);
		}
		System.out.println("avg lru future done in:" + (avgTime / 3) + "ms");
	}

	@Test
	public void testSizedCache() throws TigaseStringprepException, InterruptedException {
		long avgTime = 0;
		for (int y = 0; y < 1; y++) {
			System.gc();
			CachedPubSubRepository.SizedCache<String> cache = new CachedPubSubRepository.SizedCache<>(2000);
			Map<CachedPubSubRepository.NodeKey,String> map = Collections.synchronizedMap(cache);

			BareJID serviceJid = BareJID.bareJIDInstance("test@test.com");
			ExecutorService service = Executors.newFixedThreadPool(10);
			long start = System.currentTimeMillis();
			for (int j = 0; j < 10; j++) {
				String[] nodes = CacheTest.nodes[j];
				service.submit(() -> {
					for (int i = 0; i < 100000; i++) {
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
				service.awaitTermination(10, TimeUnit.MILLISECONDS);
			}

			long end = System.currentTimeMillis();
			System.out.println("sized done in:" + (end - start) + "ms, size:" + cache.size());

			cache.everyMinute();
			StatisticsList list = new StatisticsList(Level.FINEST);
			cache.getStatistics("tmp", list);
			System.out.println(list.toString());
			avgTime += (end - start);
		}
		System.out.println("avg sized done in:" + (avgTime / 3) + "ms");
	}

	private void sleep() {
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
