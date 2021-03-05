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

import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.config.ConfigField;
import tigase.pubsub.PubSubComponent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class implements an executor limiting number of executions of submitted tasks per second.
 */
@Bean(name = "publishExecutor", parent = PubSubComponent.class, active = true, exportable = true)
public class RateLimitingExecutor extends AbstractQueuingExecutor
		implements Runnable, Initializable {

	private static final Logger log = Logger.getLogger(RateLimitingExecutor.class.getCanonicalName());

	@ConfigField(desc = "Limit of tasks executed per second")
	private long limit = Runtime.getRuntime().availableProcessors() * 5000;

	private Thread executor = null;
	private boolean stopped = false;

	public RateLimitingExecutor() {
	}

	public long getLimit() {
		return limit;
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}

	private boolean throttling = false;

	@Override
	public void run() {
		while (!stopped) {
			long start = System.currentTimeMillis();
			long sleepTime = getSleepTime();
			long permissions = limit / (1000/sleepTime);
			for (int i=0; i<permissions; i++) {
				try {
					execute();
				} catch (InterruptedException ex) {
					// handle exception somehow..
				}
			}
			long end = System.currentTimeMillis();

			if (log.isLoggable(Level.INFO)) {
				int size = queue.totalSize();
				if (size > limit) {
					if (!throttling) {
						log.log(Level.INFO,
								"throttling executions started at rate " + permissions + " every " + sleepTime +
										"ms, current queue size " + size);
						throttling = true;
					}
				} else {
					if (throttling) {
						log.log(Level.INFO, "throttling executions ended");
					}
					throttling = false;
				}
			}

			long actualSleep = sleepTime - (end - start);
			if (actualSleep > 0) {
				try {
					Thread.sleep(actualSleep);
				} catch (InterruptedException ex) {
					// handle exception somehow..
				}
			}
		}
	}

	/**
	 * Calculate best sleep time for current limit value.
	 * @return
	 */
	protected long getSleepTime() {
		if (limit > 1000) {
			return 1;
		} else if (limit > 100) {
			return 10;
		} else if (limit > 10) {
			return 100;
		} else {
			return 1000;
		}
	}

	@Override
	public void initialize() {
		if (executor != null) {
			return;
		}
		executor = new Thread(this, "publish-executor");
		executor.setDaemon(true);
		executor.start();
	}

	@Override
	public void beforeUnregister() {
		 stopped = true;
		 super.beforeUnregister();
		 executor = null;
	}
}
