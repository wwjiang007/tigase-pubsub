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
package tigase.pubsub;

import tigase.conf.Configurable;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.sys.TigaseRuntime;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Class contains basic configuration of PubSub component used by component modules.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:10:54 GMT
 */
@Bean(name = "config", parent = PubSubComponent.class, active = true)
public class PubSubConfig implements IPubSubConfig {

	public static final String ADMINS_KEY = "admin";
	public static final String MAX_CACHE_SIZE = "pubsub-repository-cache-size";
	private static final String AUTO_SUBSCRIBE_NODE_CREATOR = "auto-subscribe-node-creator";
	private static final String PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY = "pubsub-high-memory-usage-level";
	private static final String PUBSUB_LOW_MEMORY_DELAY_KEY = "pubsub-low-memory-delay";
	private static final String PUBSUB_PEP_REMOVE_EMPTY_GEOLOC_KEY = "pep-remove-empty-geoloc";
	private static final String PUBSUB_PERSISTENT_PEP_KEY = "persistent-pep";
	private static final String PUBSUB_SEND_LAST_PUBLISHED_ITEM_ON_PRESECE_KEY = "send-last-published-item-on-presence";
	protected final Logger log = Logger.getLogger(this.getClass().getName());

	@ConfigField(desc = "List of admins", alias = "admins")
	protected String[] admins;
	protected BareJID serviceBareJID = BareJID.bareJIDInstanceNS("tigase-pubsub");
	@ConfigField(desc = "Automatically subscribe creator to node", alias = AUTO_SUBSCRIBE_NODE_CREATOR)
	private boolean autoSubscribeNodeCreator = false;
	@Inject(bean = "service")
	private PubSubComponent component;
	@ConfigField(desc = "High memory usage level", alias = PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY)
	private float highMemoryUsageLevel = 90;

	@ConfigField(desc = "Low memory delay", alias = PUBSUB_LOW_MEMORY_DELAY_KEY)
	private long lowMemoryDelay = 1000;

	@ConfigField(desc = "Max Cache size", alias = MAX_CACHE_SIZE)
	private Integer maxCacheSize = 2000;

	@ConfigField(desc = "MAM enabled")
	private boolean mamEnabled = false;

	@ConfigField(desc = "PEP Remove Empty Geoloc", alias = PUBSUB_PEP_REMOVE_EMPTY_GEOLOC_KEY)
	private boolean pepRemoveEmptyGeoloc = false;

	@ConfigField(desc = "Persistent PEP", alias = PUBSUB_PERSISTENT_PEP_KEY)
	private boolean persistentPep = true;

	@ConfigField(desc = "Send Last Published Item on Presence", alias = PUBSUB_SEND_LAST_PUBLISHED_ITEM_ON_PRESECE_KEY)
	private boolean sendLastPublishedItemOnPresence = true;

	@ConfigField(desc = "Subscribe to open nodes with presence based filtered notifications to non-PEP services like in PEP", alias = "subscribe-by-presence-filtered-notifications")
	private boolean subscribeByPresenceFilteredNotifications = false;

	@ConfigField(desc = "Trust every XMPP entity", alias = "trust-every-entity")
	private boolean trustEveryEntity;

	public String[] getAdmins() {
		return admins;
	}

	public void setAdmins(String[] strings) {
		this.admins = strings;
	}

	@Override
	public String getComponentName() {
		return component.getName();
	}

	public JID getComponentJID() {
		return this.component.getComponentId();
	}

	public long getDelayOnLowMemory() {
		if (isHighMemoryUsage()) {
			return lowMemoryDelay;
		}

		return 0;
	}

	public Integer getMaxCacheSize() {
		return maxCacheSize;
	}

	public BareJID getServiceBareJID() {
		return serviceBareJID;
	}

	public void setProperties(Map<String, Object> props) {
		if (props.containsKey(ADMINS_KEY)) {
			admins = (String[]) props.get(ADMINS_KEY);
		} else if (props.get(Configurable.GEN_ADMINS) != null) {
			admins = ((String) props.get(Configurable.GEN_ADMINS)).split(",");
		} else {
			admins = new String[]{"admin@" + component.getDefHostName()};
		}
	}

	public boolean isAutoSubscribeNodeCreator() {
		return autoSubscribeNodeCreator;
	}

	public boolean isAdmin(final BareJID jid) {
		if (trustEveryEntity) {
			return true;
		}

		if ((jid == null) || (this.admins == null)) {
			return false;
		}

		for (String adj : this.admins) {
			if (jid.toString().equals(adj)) {
				return true;
			}
		}

		return component.isTrusted(jid.toString());
	}

	public boolean isAdmin(final JID jid) {
		return isAdmin(jid.getBareJID());
	}

	public boolean isMAMEnabled() {
		return mamEnabled;
	}

	public boolean isPepPeristent() {
		return persistentPep;
	}

	public boolean isPepRemoveEmptyGeoloc() {
		return pepRemoveEmptyGeoloc;
	}

	public boolean isSendLastPublishedItemOnPresence() {
		return sendLastPublishedItemOnPresence;
	}

	public boolean isSubscribeByPresenceFilteredNotifications() {
		return subscribeByPresenceFilteredNotifications;
	}

	public boolean isHighMemoryUsage() {
		return TigaseRuntime.getTigaseRuntime().getHeapMemUsage() > highMemoryUsageLevel;
	}

}
