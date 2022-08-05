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
package tigase.pubsub.modules.commands;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractLoadRunner
		implements Runnable {

	protected final Logger log = Logger.getLogger(this.getClass().getName());
	private final long delay;

	/**
	 * Test time in seconds.
	 */
	private final long testTime;
	private int counter = 0;
	private long testEndTime;
	private long testStartTime;

	public AbstractLoadRunner(long time, long frequency) {
		this.delay = (long) ((1.0 / frequency) * 1000.0);
		this.testTime = time;
		log.log(Level.CONFIG, "Preparing load test: testTime=" + testTime + ", frequency=" + frequency + "/sec; calculatedDelay=" +
						 delay + " ms");
	}

	public int getCounter() {
		return counter;
	}

	public long getDelay() {
		return delay;
	}

	public long getTestEndTime() {
		return testEndTime;
	}

	public long getTestStartTime() {
		return testStartTime;
	}

	@Override
	public void run() {
		try {
			this.testStartTime = System.currentTimeMillis();
			this.testEndTime = testStartTime + testTime * 1000;

			long cst;
			while (testEndTime >= (cst = System.currentTimeMillis())) {
				++counter;

				doWork();

				// do not add code under this line ;-)
				final long now = System.currentTimeMillis();
				final long dt = now - cst;
				final long fix = (testStartTime + delay * (counter - 1)) - now;
				final long sleepTime = delay - dt + fix;
				// System.out.println(new Date() + " :: " + delay + ", " + dt +
				// ", " + fix + ", " + sleepTime);
				if (sleepTime > 0) {
					Thread.sleep(sleepTime);
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "LoadTest generator stopped", e);
		}
		onTestFinish();
	}

	protected abstract void doWork() throws Exception;

	protected void onTestFinish() {
	}
}
