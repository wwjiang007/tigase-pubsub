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
package tigase.pubsub.repository;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.rsm.RSM;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface description
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:20:15 GMT
 */
public interface IPubSubRepository
		extends MAMRepository<Query, MAMRepository.Item> {

	void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

	void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
					NodeType nodeType, String collection) throws RepositoryException, PubSubException;

	void createService(BareJID serviceJID, boolean isPublic) throws RepositoryException;

	List<BareJID> getServices(BareJID domain, Boolean isPublic) throws RepositoryException;

	void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException;

	void destroy();

	String[] getChildNodes(BareJID serviceJid, String node) throws RepositoryException;

	IAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException;

	AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException;

	IItems getNodeItems(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	List<IItems.IItem> getNodeItems(BareJID serviceJid, String nodeName, JID requester, Date after, Date before, RSM rsm)
			throws ComponentException, RepositoryException;
		
	INodeMeta getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException;

	ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException;

	long getNodesCount(BareJID serviceJid) throws RepositoryException;

	IPubSubDAO getPubSubDAO();

	String[] getRootCollection(BareJID serviceJid) throws RepositoryException;

	Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException;

	Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID userJid) throws RepositoryException;

	void init();

	void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

	void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig)
			throws RepositoryException, PubSubException;

	void update(BareJID serviceJid, String nodeName, IAffiliations affiliations) throws RepositoryException;

	void update(BareJID serviceJid, String nodeName, ISubscriptions subscriptions) throws RepositoryException;
	
	void deleteService(BareJID serviceJID) throws RepositoryException;

	void addMAMItem(BareJID serviceJid, String nodeName, String uuid, Element message, String itemId) throws RepositoryException;

	interface RootCollectionSetIfc {

		void add(String node);

		void remove(String node);

		Set<String> values();
		
	}

	interface RepositorySupplier<T> {

		T get() throws RepositoryException;

	}
}
