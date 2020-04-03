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

import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

public interface IPubSubConfig {

	String[] getAdmins();

	String getComponentName();

	JID getComponentJID();

	long getDelayOnLowMemory();

	Integer getMaxCacheSize();

	BareJID getServiceBareJID();

	boolean isAutoSubscribeNodeCreator();

	boolean isAdmin(final BareJID jid);

	boolean isAdmin(final JID jid);

	boolean isMAMEnabled();

	boolean isPepPeristent();

	boolean isPepRemoveEmptyGeoloc();

	boolean isSendLastPublishedItemOnPresence();

	public boolean isSubscribeByPresenceFilteredNotifications();

	boolean isHighMemoryUsage();

}
