/*
 * IPubSubDAO.java
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

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.mam.MAMRepository;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Interface of database access layer for PubSub component.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:16:25 GMT
 */
public interface IPubSubDAO<T, S extends DataSource, Q extends Query>
		extends DataSourceAware<S> {

	void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

	T createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
				 NodeType nodeType, T collectionId) throws RepositoryException;

	void deleteItem(BareJID serviceJid, T nodeId, String id) throws RepositoryException;

	void deleteNode(BareJID serviceJid, T nodeId) throws RepositoryException;

	void destroy();

	String[] getAllNodesList(BareJID serviceJid) throws RepositoryException;

	@Deprecated
	String[] getBuddyGroups(BareJID owner, BareJID bareJid) throws RepositoryException;

	@Deprecated
	String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException;

	String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException;

	Element getItem(BareJID serviceJid, T nodeId, String id) throws RepositoryException;

	Date getItemCreationDate(BareJID serviceJid, T nodeId, final String id) throws RepositoryException;

	String[] getItemsIds(BareJID serviceJid, T nodeId) throws RepositoryException;

	String[] getItemsIdsSince(BareJID serviceJid, T nodeId, Date since) throws RepositoryException;

	List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, T nodeId, String nodeName) throws RepositoryException;

	Date getItemUpdateDate(BareJID serviceJid, T nodeId, final String id) throws RepositoryException;

	NodeAffiliations getNodeAffiliations(BareJID serviceJid, T nodeId) throws RepositoryException;

	@Deprecated
	String getNodeConfig(BareJID serviceJid, T nodeId) throws RepositoryException;

	@Deprecated
	T getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException;

	INodeMeta<T> getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException;

	long getNodesCount(BareJID serviceJid) throws RepositoryException;

	String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException;

	NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, T nodeId) throws RepositoryException;

	Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid) throws RepositoryException;

	Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException;

	Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid) throws RepositoryException;

	AbstractNodeConfig parseConfig(String nodeName, String cfgData) throws RepositoryException;

	void queryItems(Q query, List<T> nodesIds, MAMRepository.ItemHandler<Q, IPubSubRepository.Item> itemHandler)
			throws RepositoryException, ComponentException;

	void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException;

	void removeService(BareJID serviceJid) throws RepositoryException;

	void removeFromRootCollection(BareJID serviceJid, T nodeId) throws RepositoryException;

	void removeNodeSubscription(BareJID serviceJid, T nodeId, BareJID jid) throws RepositoryException;

	void updateNodeAffiliation(BareJID serviceJid, T nodeId, String nodeName, UsersAffiliation userAffiliation)
			throws RepositoryException;

	void updateNodeConfig(BareJID serviceJid, final T nodeId, final String serializedData, T collectionId)
			throws RepositoryException;

	void updateNodeSubscription(BareJID serviceJid, T nodeId, String nodeName, UsersSubscription userSubscription)
			throws RepositoryException;

	void writeItem(BareJID serviceJid, T nodeId, long timeInMilis, final String id, final String publisher,
				   final Element item) throws RepositoryException;

}
