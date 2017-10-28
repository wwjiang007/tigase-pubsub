/*
 * PubSubRepositoryWrapper.java
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
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.stats.StatisticHolder;
import tigase.stats.StatisticsList;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.impl.roster.RosterElement;

import java.util.Map;

public class PubSubRepositoryWrapper implements IPubSubRepository, StatisticHolder {

	private IPubSubRepository repo;

	public PubSubRepositoryWrapper(IPubSubRepository repo) {
		this.repo = repo;
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.addToRootCollection(serviceJid, nodeName);
	}

	@Override
	public void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, String collection) throws RepositoryException {
		repo.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collection);
	}

	@Override
	public void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.deleteNode(serviceJid, nodeName);
	}

	@Override
	public void destroy() {
		repo.destroy();
	}

	@Override
	public void forgetConfiguration(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.forgetConfiguration(serviceJid, nodeName);
	}

	@Override
	public String[] getBuddyGroups(BareJID owner, BareJID buddy) throws RepositoryException {
		return repo.getBuddyGroups(owner, buddy);
	}

	@Override
	public String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException {
		return repo.getBuddySubscription(owner, buddy);
	}

	@Override
	public String[] getChildNodes(BareJID serviceJid, String node) throws RepositoryException {
		return repo.getChildNodes(serviceJid, node);
	}

	@Override
	public IAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeAffiliations(serviceJid, nodeName);
	}

	@Override
	public AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeConfig(serviceJid, nodeName);
	}

	@Override
	public IItems getNodeItems(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeItems(serviceJid, nodeName);
	}

	@Override
	public INodeMeta getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeMeta(serviceJid, nodeName);
	}

	@Override
	public ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		return repo.getNodeSubscriptions(serviceJid, nodeName);
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return repo.getPubSubDAO();
	}

	@Override
	public String[] getRootCollection(BareJID serviceJid) throws RepositoryException {
		return repo.getRootCollection(serviceJid);
	}

	@Override
	public Map<BareJID,RosterElement> getUserRoster(BareJID owner) throws RepositoryException {
		return repo.getUserRoster(owner);
	}

	@Override
	public Map<String,UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID userJid) throws RepositoryException {
		return repo.getUserSubscriptions(serviceJid, userJid);
	}
	
	@Override
	public void init() {
		repo.init();
	}

	@Override
	public void queryItems(Query query, ItemHandler<Query, Item> itemHandler)
			throws RepositoryException, ComponentException {
		repo.queryItems(query, itemHandler);
	}

	@Override
	public Query newQuery() {
		return new Query();
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		repo.removeFromRootCollection(serviceJid, nodeName);
	}
	
	@Override
	public void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		repo.update(serviceJid, nodeName, nodeConfig);
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, IAffiliations affiliations) throws RepositoryException {
		repo.update(serviceJid, nodeName, affiliations);
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, ISubscriptions subscriptions) throws RepositoryException {
		repo.update(serviceJid, nodeName, subscriptions);
	}

	@Override
	public void onUserRemoved(BareJID userJid) throws RepositoryException {
		
	}

	@Override
	public void statisticExecutedIn(long executionTime) {
		if (repo instanceof StatisticHolder) {
			((StatisticHolder) repo).statisticExecutedIn(executionTime);
		}
	}

	@Override
	public void everyHour() {
		if (repo instanceof StatisticHolder) {
			((StatisticHolder) repo).everyHour();
		}	
	}

	@Override
	public void everyMinute() {
		if (repo instanceof StatisticHolder) {
			((StatisticHolder) repo).everyMinute();
		}	}

	@Override
	public void everySecond() {
		if (repo instanceof StatisticHolder) {
			((StatisticHolder) repo).everySecond();
		}
	}

	@Override
	public void getStatistics(String compName, StatisticsList list) {
		if (repo instanceof StatisticHolder) {
			((StatisticHolder) repo).getStatistics(compName, list);
		}
	}

	@Override
	public void setStatisticsPrefix(String prefix) {
		if (repo instanceof StatisticHolder) {
			((StatisticHolder) repo).setStatisticsPrefix(prefix);
		}
	}
}