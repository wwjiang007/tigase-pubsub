/*
 * IPubSubRepository.java
 *
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

package tigase.pubsub.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.mam.MAMRepository;

import java.util.Map;

/**
 * Interface description
 *
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 * @version 5.0.0, 2010.03.27 at 05:20:15 GMT
 */
public interface IPubSubRepository
		extends MAMRepository<Query, IPubSubRepository.Item> {

	void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

	void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
					NodeType nodeType, String collection) throws RepositoryException;

	void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException;

	void destroy();

	void forgetConfiguration(BareJID serviceJid, String nodeName) throws RepositoryException;

	@Deprecated
	String[] getBuddyGroups(BareJID owner, BareJID buddy) throws RepositoryException;

	@Deprecated
	String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException;

	String[] getChildNodes(BareJID serviceJid, String node) throws RepositoryException;

	IAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException;

	AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException;

	IItems getNodeItems(BareJID serviceJid, String nodeName) throws RepositoryException;

	INodeMeta getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException;

	ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException;

	long getNodesCount(BareJID serviceJid) throws RepositoryException;

	IPubSubDAO getPubSubDAO();

	String[] getRootCollection(BareJID serviceJid) throws RepositoryException;

	Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException;

	Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID userJid) throws RepositoryException;

	void init();

	void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

	void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException;

	void update(BareJID serviceJid, String nodeName, IAffiliations affiliations) throws RepositoryException;

	void update(BareJID serviceJid, String nodeName, ISubscriptions subscriptions) throws RepositoryException;

	void onUserRemoved(BareJID userJid) throws RepositoryException;

	interface Item
			extends MAMRepository.Item {

		String getItemId();

		String getNode();

	}
}
