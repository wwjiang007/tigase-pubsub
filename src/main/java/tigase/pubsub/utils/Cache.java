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

import tigase.stats.StatisticHolder;

import java.util.Set;
import java.util.stream.Stream;

public interface Cache<K,V> extends StatisticHolder {

	V computeIfAbsent(K key, CacheSupplier<V> supplier) throws CacheException;
	V get(K key);
	V put(K key, V value);
	V putIfAbsent(K key, V value);
	V remove(K key);

	Set<K> keySet();
	Stream<V> values();

	int size();
	void setMaxSize(int maxSize);

	@FunctionalInterface
	interface CacheSupplier<V> {
		V get() throws CacheException;
	}

	class CacheException extends Exception {

		public CacheException(Throwable ex) {
			super(ex.getMessage(), ex);
		}

	}
}
