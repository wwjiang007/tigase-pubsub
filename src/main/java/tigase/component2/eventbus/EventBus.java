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

public abstract class EventBus {

	public abstract <H extends EventHandler> void addHandler(EventType<H> type, H handler);

	public abstract <H extends EventHandler> void addHandler(EventType<H> type, Object source, H handler);

	public abstract <H extends EventHandler> void addListener(EventListener listener);

	public abstract <H extends EventHandler> void addListener(EventType<H> type, EventListener listener);

	public abstract <H extends EventHandler> void addListener(EventType<H> type, Object source, EventListener listener);

	public abstract void fire(Event<?> e);

	public abstract void fire(Event<?> e, Object source);

	public abstract void remove(EventHandler handler);

	public abstract void remove(EventType<?> type, EventHandler handler);

	public abstract void remove(EventType<?> type, Object source, EventHandler handler);

	protected void setEventSource(Event<EventHandler> event, Object source) {
		event.setSource(source);
	}
	
	public abstract void reset();
}
