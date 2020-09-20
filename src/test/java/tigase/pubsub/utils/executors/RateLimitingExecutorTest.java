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
package tigase.pubsub.utils.executors;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

// Test disable as it does not work well in a multi-threaded environment
@Ignore
public class RateLimitingExecutorTest {

	@Test
	public void test10ps() throws InterruptedException {
		AtomicInteger counter = new AtomicInteger(0);
		RateLimitingExecutor executor = new RateLimitingExecutor();
		executor.setLimit(10);

		for (int i=0; i<100; i++){
			executor.submit(Executor.Priority.normal, () -> {
				counter.incrementAndGet();
			});
		}

		executor.initialize();
		Thread.sleep(1000);
		int count = counter.get();
		executor.beforeUnregister();

		assertEquals(10, count);
	}

	@Test
	public void test100ps() throws InterruptedException {
		AtomicInteger counter = new AtomicInteger(0);
		RateLimitingExecutor executor = new RateLimitingExecutor();
		executor.setLimit(100);

		for (int i=0; i<1000; i++){
			executor.submit(Executor.Priority.normal, () -> {
				counter.incrementAndGet();
			});
		}

		executor.initialize();
		Thread.sleep(100);
		int count = counter.get();
		executor.beforeUnregister();

		assertEquals(10, count);
	}
	@Test
	public void test1000ps() throws InterruptedException {
		AtomicInteger counter = new AtomicInteger(0);
		RateLimitingExecutor executor = new RateLimitingExecutor();
		executor.setLimit(1000);

		for (int i=0; i<10000; i++){
			executor.submit(Executor.Priority.normal, () -> {
				counter.incrementAndGet();
			});
		}

		executor.initialize();
		Thread.sleep(100);
		int count = counter.get();
		executor.beforeUnregister();

		assertEquals(100, count);
	}
}
