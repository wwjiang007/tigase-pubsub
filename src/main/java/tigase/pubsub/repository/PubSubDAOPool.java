/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2009-2016 "Tigase, Inc." <office@tigase.com>
 * Copyright (C) 2009 "Tomasz Sterna" <tomek@xiaoka.com>
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
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.pubsub.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.db.DBInitException;
import tigase.db.DataSource;
import tigase.db.DataSourceHelper;
import tigase.db.beans.MDRepositoryBean;
import tigase.kernel.beans.Bean;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.impl.roster.RosterElement;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name="dao", parent = PubSubComponent.class)
public class PubSubDAOPool<T, S extends DataSource> extends MDRepositoryBean<IPubSubDAO<T, S>> implements IPubSubDAO<T, S>  {

	private static final Logger log = Logger.getLogger(PubSubDAOPool.class.getName());

	/**
	 * Variable destroyed is set to true to ensure that all JDBC connections
	 * will be closed and even if some of them were taken for execution in
	 * moment of pool being destroyed.
	 */
	private boolean destroyed = false;

	public PubSubDAOPool() {
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.addToRootCollection(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public T createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType,
			T collectionId) throws RepositoryException {
		IPubSubDAO<T, DataSource> dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collectionId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
			return null;
		}
	}

	@Override
	public void deleteItem(BareJID serviceJid, T nodeId, String id) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.deleteItem(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void deleteNode(BareJID serviceJid, T nodeId) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.deleteNode(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void destroy() {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "destroying IPubSubDAOPool {0}", this);
		}
		destroyed = true;
	}

	@Override
	public String[] getAllNodesList(BareJID serviceJid) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getAllNodesList(serviceJid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public String[] getBuddyGroups(BareJID owner, BareJID bareJid) throws RepositoryException {
		IPubSubDAO dao = takeDao(null);
		if (dao != null) {
			return dao.getBuddyGroups(owner, bareJid);
		}
		return null;
	}

	@Override
	public String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException {
		IPubSubDAO dao = takeDao(null);
		if (dao != null) {
			return dao.getBuddySubscription(owner, buddy);
		}
		return null;
	}

	@Override
	public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getChildNodes(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Element getItem(BareJID serviceJid, T nodeId, String id) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItem(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Date getItemCreationDate(BareJID serviceJid, final T nodeId, final String id) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemCreationDate(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public String[] getItemsIds(BareJID serviceJid, T nodeId) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemsIds(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public String[] getItemsIdsSince(BareJID serviceJid, T nodeId, Date since) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemsIdsSince(serviceJid, nodeId, since);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, T nodeId, String nodeName) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemsMeta(serviceJid, nodeId, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Date getItemUpdateDate(BareJID serviceJid, T nodeId, String id) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemUpdateDate(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public NodeAffiliations getNodeAffiliations(BareJID serviceJid, T nodeId) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeAffiliations(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public String getNodeConfig(BareJID serviceJid, T nodeId) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeConfig(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
			return null;
		}
	}

	@Override
	public T getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException {
		IPubSubDAO<T, DataSource> dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeId(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
			return null;
		}
	}

	@Override
	public INodeMeta<T> getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException {
		IPubSubDAO<T, DataSource> dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeMeta(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
			return null;
		}
	}

	@Override
	public String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodesList(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, T nodeId) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeSubscriptions(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Deprecated
	protected String getPoolDetails(BareJID serviceJid) {
		return "";
	}

	@Override
	public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getUserAffiliations(serviceJid, jid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException {
		return null;
	}

	@Override
	public Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getUserSubscriptions(serviceJid, jid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public AbstractNodeConfig parseConfig(String nodeName, String cfgData) throws RepositoryException {
		return null;
	}

	@Deprecated
	protected void offerDao(BareJID serviceJid, IPubSubDAO dao) {
		if (destroyed) {
			dao.destroy();
			return;
		}
	}

	/*
	 * //@Override protected String readNodeConfigFormData(BareJID serviceJid,
	 * final long nodeId) throws TigaseDBException { IPubSubDAO dao =
	 * takeDao(serviceJid); if (dao != null) { try { return
	 * dao.readNodeConfigFormData(serviceJid, nodeName); } finally {
	 * offerDao(serviceJid, dao); } } else { log.warning(
	 * "dao is NULL, pool empty? - " + getPoolDetails(serviceJid)); } return
	 * null; }
	 */

	@Override
	public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.removeAllFromRootCollection(serviceJid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, T nodeId) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.removeFromRootCollection(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void removeNodeSubscription(BareJID serviceJid, T nodeId, BareJID jid) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.removeNodeSubscription(serviceJid, nodeId, jid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	public IPubSubDAO takeDao(BareJID serviceJid) {
		return getRepository(serviceJid.getDomain());
	}

	@Override
	public void updateNodeAffiliation(BareJID serviceJid, T nodeId, String nodeName, UsersAffiliation affiliation)
			throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.updateNodeAffiliation(serviceJid, nodeId, nodeName, affiliation);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void updateNodeConfig(final BareJID serviceJid, final T nodeId, final String serializedData, final T collectionId)
			throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.updateNodeConfig(serviceJid, nodeId, serializedData, collectionId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void updateNodeSubscription(BareJID serviceJid, T nodeId, String nodeName, UsersSubscription subscription)
			throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.updateNodeSubscription(serviceJid, nodeId, nodeName, subscription);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void writeItem(final BareJID serviceJid, T nodeId, long timeInMilis, final String id, final String publisher,
			final Element item) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.writeItem(serviceJid, nodeId, timeInMilis, id, publisher, item);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void removeService(BareJID serviceJid) throws RepositoryException {
		IPubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.removeService(serviceJid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}	}

	@Override
	public void setDataSource(DataSource dataSource) {
	   	// This is pool so there is nothing to do here
	}

	@Override
	protected Class<? extends IPubSubDAO<T, S>> findClassForDataSource(DataSource dataSource) throws DBInitException {
		Class cls = DataSourceHelper.getDefaultClass(PubSubDAO.class, dataSource.getResourceUri());
		return (Class<PubSubDAO<T, S>>) cls;
	}
}
