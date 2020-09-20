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

/**
 * Executor of submitted tasks with support for task priorities
 */
public interface Executor {

	/**
	 * Checks if executor is overloaded.
	 * If returns true, then you should not try to submit any new tasks for a while.
	 * @return true - executor is overloaded
	 */
	boolean isOverloaded();

	/**
	 * Submits a task with a priority for execution.
	 * @param priority
	 * @param runnable
	 */
	void submit(Priority priority, Runnable runnable);

	public enum Priority{
		high,
		normal,
		low;

		public static Priority valueof(String name) {
			if (name == null) {
				return Priority.normal;
			}
			switch (name) {
				case "high":
					return Priority.high;
				case "low":
					return Priority.low;
				default:
					return Priority.normal;
			}
		}
	}
	
}
