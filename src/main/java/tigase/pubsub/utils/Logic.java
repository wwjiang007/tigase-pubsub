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
package tigase.pubsub.utils;

import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Map;

/**
 * Interface of a bean which implements PubSub logic.
 * <p>
 * Created by andrzej on 25.12.2016.
 */
public interface Logic {

	void checkAccessPermission(BareJID serviceJid, String nodeName, JID senderJid)
			throws PubSubException, RepositoryException;

	void checkAccessPermission(BareJID serviceJid, AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations,
							   ISubscriptions nodeSubscriptions, JID senderJid)
			throws PubSubException, RepositoryException;

	boolean hasSenderSubscription(final BareJID bareJid, final IAffiliations affiliations,
								  final ISubscriptions subscriptions) throws RepositoryException;

	boolean isSenderInRosterGroup(BareJID bareJid, AbstractNodeConfig nodeConfig, IAffiliations affiliations,
								  final ISubscriptions subscriptions) throws RepositoryException;

	Element prepareNotificationMessage(JID from, JID to, String id, Element itemToSend, Map<String, String> headers);

	default void checkNodeCreationAllowed(BareJID serviceJid, BareJID userJid, String node, String collection) throws PubSubException {
	}

}
