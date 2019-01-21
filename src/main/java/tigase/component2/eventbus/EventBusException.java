/**
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
package tigase.component2.eventbus;

import java.util.Set;

public class EventBusException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	protected static String createMessage(Set<Throwable> causes) {
		if (causes.isEmpty()) {
			return null;
		}

		StringBuilder b = new StringBuilder();

		int c = causes.size();
		if (c == 1) {
			b.append("Exception caught: ");
		} else {
			b.append(c).append(" exceptions caught: ");
		}

		boolean first = true;
		for (Throwable t : causes) {
			if (first) {
				first = false;
			} else {
				b.append("; ");
			}
			b.append(t.getMessage());
		}

		return b.toString();
	}

	protected static Throwable createThrowable(Set<Throwable> causes) {
		if (causes.isEmpty()) {
			return null;
		}
		return causes.iterator().next();
	}

	private final Set<Throwable> causes;

	public EventBusException(Set<Throwable> causes) {
		super(createMessage(causes), createThrowable(causes));
		this.causes = causes;
	}

	public Set<Throwable> getCauses() {
		return causes;
	}

}
