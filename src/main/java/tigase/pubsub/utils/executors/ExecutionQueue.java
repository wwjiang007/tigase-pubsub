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

import tigase.util.workqueue.PriorityQueueRelaxed;

/**
 * Class implements tasks queue with priorities.
 */
public class ExecutionQueue
		extends PriorityQueueRelaxed<Runnable> {

	public ExecutionQueue() {
		super(Executor.Priority.values().length, Integer.MAX_VALUE);
	}

	public boolean offer(Executor.Priority priority, Runnable task) {
		return super.offer(task, priority.ordinal());
	}

	public void put(Executor.Priority priority, Runnable task) throws InterruptedException {
		super.put(task, priority.ordinal());
	}

}
