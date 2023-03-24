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

import tigase.stats.Counter;
import tigase.stats.StatisticsList;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * This class is implementation of <code>Cache</code> interface. The main difference between <code>LRUCache</code> and
 * <code>LRUCacheWithFuture</code> is <code>LRUCache</code> implementation of
 * <code>computeIfAbsent(K key, CacheSupplier)</code>.
 *
 * In case when there is no value, <code>LRUCache</code> executes provided instance of <code>CacheSupplier</code> in
 * a synchronized block resulting in blocking of all access to the cache while result to "put" in being generated.
 *
 * On the other hand, <code>LRUCacheWithFuture</code>, when there is no result, puts instance of
 * a <code>CompletableFuture</code> in the cache (instance of <code>LRUCache</code>) as a value and then waits for
 * the completion of the <code>CacheSupplier</code> to return the value. In this implementation, long process of
 * generating value by <code>CacheSupplier</code> is not blocking access to the cache for other "keys". Parallel
 * requests to get value for the same key will be "lock" and <code>CacheSupplier</code> will be called only once
 * if the value is not in the cache.
 *
 * @param <K>
 * @param <V>
 */
public class LRUCache<K,V> implements Cache<K,V> {
	
	private final Map<K,Node<K,V>> cache;
	private final AtomicInteger size = new AtomicInteger(0);
	private int maxSize;

	private final Node<K,V> head;
	private final Node<K,V> tail;

	private Counter hitsCounter = new Counter("cache/hits", Level.FINEST);
	private Counter requestsCounter = new Counter("cache/requests", Level.FINEST);


	public LRUCache() {
		this(2000);
	}

	public LRUCache(int maxSize) {
		cache = new LinkedHashMap<>(maxSize, 0.1f);
		head = new Node<>();
		tail = new Node<>();
		head.next = tail;
		tail.prev = head;
		this.maxSize = maxSize;
	}

	public V computeIfAbsent(K key, CacheSupplier<V> supplier) throws CacheException {
		synchronized (this) {
			requestsCounter.inc();
			Node<K,V> node = cache.get(key);
			if (node != null) {
				removeFromQueue(node);
				appendToQueue(node);
				hitsCounter.inc();
				return node.value;
			} else {
				Node<K,V> newNode = newNode(key, supplier.get());
				cache.put(key, newNode);
				appendToQueue(newNode);
				size.incrementAndGet();
				while (size.get() > maxSize) {
					removeFirst();
				}
				return newNode.value;
			}
		}
	}
	
	public V get(K key) {
		requestsCounter.inc();
		synchronized (this) {
			Node<K,V> node = cache.get(key);
			if (node != null) {
				removeFromQueue(node);
				appendToQueue(node);
				hitsCounter.inc();
				return node.value;
			} else {
				return null;
			}
		}
	}

	public V put(K key, V value) {
		Node<K,V> newNode = newNode(key, value);
		synchronized (this) {
			Node<K,V> oldNode = cache.put(key, newNode);
			if (oldNode != null) {
				removeFromQueue(oldNode);
			} else {
				size.incrementAndGet();
			}
			appendToQueue(newNode);
			while (size.get() > maxSize) {
				removeFirst();
			}
			return oldNode == null ? null : oldNode.value;
		}
	}

	@Override
	public V putIfAbsent(K key, V value) {
		Node<K,V> newNode = newNode(key, value);
		synchronized (this) {
			requestsCounter.inc();
			Node<K,V> oldNode = cache.putIfAbsent(key, newNode);
			if (oldNode == null) {
				appendToQueue(newNode);
				size.incrementAndGet();
				while (size.get() > maxSize) {
					removeFirst();
				}
				return null;
			}
			hitsCounter.inc();
			return oldNode.value;
		}
	}

	public V remove(K key) {
		synchronized (this) {
			Node<K, V> node = cache.remove(key);
			if (node != null) {
				removeFromQueue(node);
				size.decrementAndGet();
			}
			return node == null ? null : node.value;
		}
	}

	public boolean remove(K key, V value) {
		synchronized (this) {
			Node<K, V> node = cache.get(key);
			if (node != null && node.value == value) {
				cache.remove(key, node);
				removeFromQueue(node);
				size.decrementAndGet();
				return true;
			} else {
				return false;
			}
		}
	}

	@Override
	public Set<K> keySet() {
		synchronized (this) {
			return new HashSet<>(cache.keySet());
		}
	}

	public Stream<V> values() {
		List<Node<K,V>> list;
		synchronized (this) {
			list = new ArrayList<>(cache.values());
		}
		return list.stream().map(Node::getValue);
	}

	@Override
	public int size() {
		return size.get();
	}

	public void setMaxSize(int size) {
		synchronized (this) {
			this.maxSize = size;
			while (this.size.get() > maxSize) {
				removeFirst();
			}
		}
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

	private void appendToQueue(Node<K,V> newNode) {
		newNode.prev = tail.prev;
		tail.prev.next = newNode;
		tail.prev = newNode;
		newNode.next = tail;
	}

	private void removeFromQueue(Node<K,V> node) {
		if (node.prev == null || node.next == null) {
			return;
		}
		final Node<K,V> prev = node.prev;
		final Node<K,V> next = node.next;
		prev.next = next;
		next.prev = prev;
	}

	private void removeFirst() {
		Node<K,V> node = head.next;
		if (node == tail) {
			return;
		}
		removeFromQueue(node);
		cache.remove(node.key);
		size.decrementAndGet();
	}

	private Node<K,V> newNode(K key, V value) {
		Node<K,V> node = new Node<>();
		node.key = key;
		node.value = value;
		return node;
	}

	private static class Node<K,V> {
		Node<K,V> prev;
		Node<K,V> next;
		K key;
		V value;

		V getValue() {
			return value;
		}
	}

}
