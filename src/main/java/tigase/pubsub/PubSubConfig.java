/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.pubsub;

import java.util.Map;
import java.util.logging.Logger;

import tigase.component.PropertiesBeanConfigurator;
import tigase.conf.Configurable;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.sys.TigaseRuntime;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * Class description
 *
 *
 * @version 5.0.0, 2010.03.27 at 05:10:54 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
@Bean(name = "pubsubConfig")
public class PubSubConfig implements Initializable {

	public static final String ADMINS_KEY = "admin";
	private static final String MAX_CACHE_SIZE = "pubsub-repository-cache-size";
	private static final String PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY = "pubsub-high-memory-usage-level";
	private static final String PUBSUB_LOW_MEMORY_DELAY_KEY = "pubsub-low-memory-delay";
	private static final String PUBSUB_PEP_REMOVE_EMPTY_GEOLOC_KEY = "pep-remove-empty-geoloc";
	private static final String PUBSUB_PERSISTENT_PEP_KEY = "persistent-pep";
	private static final String PUBSUB_SEND_LAST_PUBLISHED_ITEM_ON_PRESECE_KEY = "send-last-published-item-on-presence";
	protected final Logger log = Logger.getLogger(this.getClass().getName());

	@ConfigField(desc = "List of admins")
	protected String[] admins;
	protected BareJID serviceBareJID = BareJID.bareJIDInstanceNS("tigase-pubsub");
	@Inject
	private PubSubComponent component;
	@Inject(nullAllowed = true)
	private PropertiesBeanConfigurator configurator;

	@ConfigField(desc = "High memory usage level", alias = PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY)
	private float highMemoryUsageLevel = 90;

	@ConfigField(desc = "Low memory delay", alias = PUBSUB_LOW_MEMORY_DELAY_KEY)
	private long lowMemoryDelay = 1000;

	@ConfigField(desc = "Max Cache size", alias = MAX_CACHE_SIZE)
	private Integer maxCacheSize = 2000;

	@ConfigField(desc = "PEP Remove Empty Geoloc", alias = PUBSUB_PEP_REMOVE_EMPTY_GEOLOC_KEY)
	private boolean pepRemoveEmptyGeoloc = false;

	@ConfigField(desc = "Persistent PEP", alias = PUBSUB_PERSISTENT_PEP_KEY)
	private boolean persistentPep = false;

	@ConfigField(desc = "Send Last Published Item on Presence", alias = PUBSUB_SEND_LAST_PUBLISHED_ITEM_ON_PRESECE_KEY)
	private boolean sendLastPublishedItemOnPresence = false;

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String[] getAdmins() {
		return admins;
	}

	/**
	 * Method description
	 *
	 *
	 * @param strings
	 */
	public void setAdmins(String[] strings) {
		this.admins = strings;
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

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public BareJID getServiceBareJID() {
		return serviceBareJID;
	}

	@Override
	public void initialize() {
		if (configurator != null) {
			final Map<String, Object> props = configurator.getProperties();
			setProperties(props);
		} else {
			log.warning("Configurator was not injected!");
		}
	}

	public void setProperties(Map<String, Object> props) {
		if (props.containsKey(ADMINS_KEY)) {
			admins = (String[]) props.get(ADMINS_KEY);
		} else if (props.get(Configurable.GEN_ADMINS) != null) {
			admins = ((String) props.get(Configurable.GEN_ADMINS)).split(",");
		} else {
			admins = new String[] { "admin@" + component.getDefHostName() };
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 */
	public boolean isAdmin(final BareJID jid) {
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

	private boolean isHighMemoryUsage() {
		return TigaseRuntime.getTigaseRuntime().getHeapMemUsage() > highMemoryUsageLevel;
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

}
