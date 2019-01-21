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
package tigase.component2;

import java.util.Map;

import tigase.component2.eventbus.EventBus;
import tigase.xmpp.BareJID;

public abstract class ComponentConfig {

	protected final AbstractComponent<?> component;

	private BareJID serviceName;

	protected ComponentConfig(AbstractComponent<?> component) {
		this.component = component;
	}

	public abstract Map<String, Object> getDefaults(Map<String, Object> params);

	public EventBus getEventBus() {
		return component.getEventBus();
	}

	public BareJID getServiceName() {
		if (serviceName == null) {
			serviceName = BareJID.bareJIDInstanceNS(component.getName());
		}
		return serviceName;
	}

	public abstract void setProperties(Map<String, Object> props);
}
