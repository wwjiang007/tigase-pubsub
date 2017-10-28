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

import java.util.Map;

import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.mam.MAMRepository;

/**
 * Interface description
 *
 *
 * @version 5.0.0, 2010.03.27 at 05:20:15 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public interface IPubSubRepository extends MAMRepository<Query, IPubSubRepository.Item> {

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @throws RepositoryException
	 */
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 * @param ownerJid
	 * @param nodeConfig
	 * @param nodeType
	 * @param collection
	 *
	 * @throws RepositoryException
	 */
	public abstract void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, String collection) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @throws RepositoryException
	 */
	public abstract void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 */
	public void destroy();

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @throws RepositoryException
	 */
	public abstract void forgetConfiguration(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param bareJid
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	@Deprecated
	public abstract String[] getBuddyGroups(BareJID owner, BareJID buddy) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param buddy
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	@Deprecated
	public abstract String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException;

	public String[] getChildNodes(BareJID serviceJid, String node) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public IAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public abstract AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public IItems getNodeItems(BareJID serviceJid, String nodeName) throws RepositoryException;

	public INodeMeta getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public abstract IPubSubDAO getPubSubDAO();

	/**
	 * Method description
	 *
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public abstract String[] getRootCollection(BareJID serviceJid) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param owner
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public abstract Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException;

	public abstract Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID userJid)
			throws RepositoryException;

	/**
	 * Method description
	 *
	 */
	public abstract void init();

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 *
	 * @throws RepositoryException
	 */
	public void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 * @param nodeConfig
	 *
	 * @throws RepositoryException
	 */
	public abstract void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 * @param affiliations
	 *
	 * @throws RepositoryException
	 */
	public void update(BareJID serviceJid, String nodeName, IAffiliations affiliations) throws RepositoryException;

	/**
	 * Method description
	 *
	 *
	 * @param nodeName
	 * @param subscriptions
	 *
	 * @throws RepositoryException
	 */
	public void update(BareJID serviceJid, String nodeName, ISubscriptions subscriptions) throws RepositoryException;
	
	public void onUserRemoved(BareJID userJid) throws RepositoryException;

	interface Item extends MAMRepository.Item {

		String getItemId();

		String getNode();

	}
}
