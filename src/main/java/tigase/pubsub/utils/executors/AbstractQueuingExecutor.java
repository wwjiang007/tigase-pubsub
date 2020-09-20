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

import tigase.kernel.beans.UnregisterAware;

/**
 * Abstract class providing implementation of executor supporting task queueing and prioritization.
 */
public class AbstractQueuingExecutor implements Executor, UnregisterAware {

	protected final ExecutionQueue queue = new ExecutionQueue();

	@Override
	public boolean isOverloaded() {
		// TODO: maybe different value should be used but queue size is limited to Integer.MAX_VALUE
		// so after using half of the capacity we should stop processing new publications
		// TODO: should we consider also the amount of free heap?
		return queue.totalSize() > Integer.MAX_VALUE;
	}

	@Override
	public void submit(Priority priority, Runnable runnable) {
		try {
			queue.put(priority, runnable);
		} catch (InterruptedException ex) {
			// handle exception somehow..
		}
	}

	@Override
	public void beforeUnregister() {
		synchronized (queue) {
			queue.notifyAll();
		}
	}

	/**
	 * Method called by subclass to execute a single task from the queue or wait for any task to appear.
	 * @throws InterruptedException
	 */
	protected void execute() throws InterruptedException {
		Runnable run = queue.take();
		if (run != null) {
			run.run();
		}
	}

}
