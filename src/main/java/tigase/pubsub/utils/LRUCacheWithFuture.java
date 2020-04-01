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

import tigase.stats.StatisticsList;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

public class LRUCacheWithFuture<K,V> implements Cache<K,V> {

	private final LRUCache<K,CompletableFuture<V>> cache;

	public LRUCacheWithFuture() {
		this(2000);
	}

	public LRUCacheWithFuture(int maxSize) {
		cache = new LRUCache<>(maxSize);
	}

	public V computeIfAbsent(K key, CacheSupplier<V> supplier) throws CacheException {
//		requestsCounter.inc();

		CompletableFuture<V> newFuture = new CompletableFuture<>();
		CompletableFuture<V> oldFuture = cache.putIfAbsent(key, newFuture);
		if (oldFuture != null) {
			return oldFuture.join();
		}

		try {
			newFuture.complete(supplier.get());
			return newFuture.join();
		} catch (CacheException ex) {
			newFuture.completeExceptionally(ex);
			cache.remove(key);
			throw ex;
		}
	}

	public V get(K key) {
		CompletableFuture<V> future = cache.get(key);
		if (future == null) {
			return null;
		}

		if (future.isCompletedExceptionally()) {
			cache.remove(key);
			return null;
		}
		try {
			return future.join();
		} catch (CompletionException ex) {
			cache.remove(key);
			throw ex;
		}
	}

	public V put(K key, V value) {
		CompletableFuture<V> node = cache.put(key, CompletableFuture.completedFuture(value));
		try {
			return (node == null || node.isCompletedExceptionally()) ? null : node.join();
		} catch (CompletionException ex) {
			cache.remove(key);
			throw ex;
		}
	}

	public V putIfAbsent(K key, V value) {
		CompletableFuture<V> node = cache.putIfAbsent(key, CompletableFuture.completedFuture(value));
		try {
			return (node == null || node.isCompletedExceptionally()) ? null : node.join();
		} catch (CompletionException ex) {
			cache.remove(key);
			throw ex;
		}
	}

	public V remove(K key) {
		CompletableFuture<V> node = cache.remove(key);
		try {
			return (node == null || node.isCompletedExceptionally()) ? null : node.join();
		} catch (CompletionException ex) {
			cache.remove(key);
			throw ex;
		}
	}

	public Set<K> keySet() {
		return cache.keySet();
	}

	public Stream<V> values() {
		return cache.values().filter(CompletableFuture::isDone).map(CompletableFuture::join);
	}

	public int size() {
		return cache.size();
	}

	public void setMaxSize(int size) {
		cache.setMaxSize(size);
	}

	@Override
	public void everyHour() {
		cache.everyHour();
	}

	@Override
	public void everyMinute() {
		cache.everyMinute();
	}

	@Override
	public void everySecond() {
		cache.everySecond();
	}

	@Override
	public void getStatistics(String compName, StatisticsList list) {
		cache.getStatistics(compName, list);
	}

	@Override
	public void setStatisticsPrefix(String prefix) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void statisticExecutedIn(long executionTime) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

}
