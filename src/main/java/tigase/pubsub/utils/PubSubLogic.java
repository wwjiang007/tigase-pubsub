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
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Interface of a bean which implements PubSub logic.
 * <p>
 * Created by andrzej on 25.12.2016.
 */
public interface PubSubLogic {

	boolean isServiceAutoCreated();

	void checkNodeConfig(AbstractNodeConfig nodeConfig) throws PubSubException;
	
	boolean hasSenderSubscription(final BareJID bareJid, final IAffiliations affiliations) throws RepositoryException;

	boolean isSenderInRosterGroup(BareJID bareJid, AbstractNodeConfig nodeConfig, IAffiliations affiliations,
								  final ISubscriptions subscriptions) throws RepositoryException;

	Element prepareNotificationMessage(JID from, String id, String uuid, String nodeName, Element items, String expireAt,
									   Map<String, String> headers, StanzaType stanzaType);
	
	void checkPermission(BareJID serviceJid, String nodeName, JID senderJid, Action action) throws PubSubException, RepositoryException;

	Stream<JID> subscribersOfNotifications(BareJID serviceJid, String nodeName) throws RepositoryException;

	boolean isServiceJidPEP(BareJID serivceJid);

	boolean isMAMEnabled(BareJID serviceJid, String node) throws RepositoryException;

	enum Action {
		subscribe,
		unsubscribe,
		retrieveItems,
		publishItems,
		retractItems,
		purgeNode,
		manageNode
	}

	String validateItemId(BareJID toJid, String nodeName, String id);
//	enum Role {
//		none,
//		subscriber,
//		member,
//		publisher,
//		retracter,
//		owner
//	}
}
