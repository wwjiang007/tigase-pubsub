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
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.db.TigaseDBException;
import tigase.db.util.RepositoryVersionAware;
import tigase.kernel.beans.config.ConfigField;
import tigase.pubsub.*;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.NodeMeta;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.rsm.RSM;

import java.sql.*;
import java.util.Date;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;

@Repository.Meta(supportedUris = {"jdbc:[^:]+:.*"})
@Repository.SchemaId(id = Schema.PUBSUB_SCHEMA_ID, name = Schema.PUBSUB_SCHEMA_NAME)
public class PubSubDAOJDBC
		extends PubSubDAO<Long, DataRepository, Query>
		implements RepositoryVersionAware {

	private static final String CREATE_NODE_QUERY = "{ call TigPubSubCreateNode(?, ?, ?, ?, ?, ?, ?, ?, ?) }";
	private static final String REMOVE_NODE_QUERY = "{ call TigPubSubRemoveNode(?) }";
	private static final String CREATE_SERVICE_QUERY = "{ call TigPubSubCreateService(?, ?, ?) }";
	private static final String REMOVE_SERVICE_QUERY = "{ call TigPubSubRemoveService(?, ?) }";
	private static final String GET_SERVICES_QUERY = "{ call TigPubSubGetServices(?,?) }";
	private static final String GET_NODE_ID_QUERY = "{ call TigPubSubGetNodeId(?, ?) }";
	private static final String GET_NODE_META_QUERY = "{ call TigPubSubGetNodeMeta(?, ?) }";
	private static final String GET_ITEM_QUERY = "{ call TigPubSubGetItem(?, ?) }";
	private static final String WRITE_ITEM_QUERY = "{ call TigPubSubWriteItem(?, ?, ?, ?, ?, ?) }";
	private static final String DELETE_ITEM_QUERY = "{ call TigPubSubDeleteItem(?, ?) }";
	private static final String GET_NODE_ITEM_IDS_QUERY = "{ call TigPubSubGetNodeItemsIds(?,?) }";
	private static final String GET_NODE_ITEM_IDS_SINCE_QUERY = "{ call TigPubSubGetNodeItemsIdsSince(?,?,?) }";
	private static final String GET_NODE_ITEMS_META_QUERY = "{ call TigPubSubGetNodeItemsMeta(?) }";
	private static final String COUNT_NODES_QUERY = "{ call TigPubSubCountNodes(?) }";
	private static final String GET_ALL_NODES_QUERY = "{ call TigPubSubGetAllNodes(?) }";
	private static final String GET_ROOT_NODES_QUERY = "{ call TigPubSubGetRootNodes(?) }";
	private static final String GET_CHILD_NODES_QUERY = "{ call TigPubSubGetChildNodes(?,?) }";
	private static final String SET_NODE_CONFIGURATION_QUERY = "{ call TigPubSubSetNodeConfiguration(?, ?, ?) }";
	private static final String SET_NODE_AFFILIATION_QUERY = "{ call TigPubSubSetNodeAffiliation(?, ?, ?) }";
	private static final String GET_NODE_AFFILIATIONS_QUERY = "{ call TigPubSubGetNodeAffiliations(?) }";
	private static final String GET_NODE_SUBSCRIPTIONS_QUERY = "{ call TigPubSubGetNodeSubscriptions(?) }";
	private static final String SET_NODE_SUBSCRIPTION_QUERY = "{ call TigPubSubSetNodeSubscription(?, ?, ?, ?) }";
	private static final String DELETE_NODE_SUBSCRIPTIONS_QUERY = "{ call TigPubSubDeleteNodeSubscription(?, ?) }";
	private static final String GET_USER_AFFILIATIONS_QUERY = "{ call TigPubSubGetUserAffiliations(?, ?) }";
	private static final String GET_USER_SUBSCRIPTIONS_QUERY = "{ call TigPubSubGetUserSubscriptions(?, ?) }";

	private static final String COUNT_NODES_ITEMS_QUERY = "{ call TigPubSubQueryItemsCount(?,?,?) }";
	private static final String GET_NODES_ITEMS_QUERY = "{ call TigPubSubQueryItems(?,?,?,?) }";
	private static final String GET_NODES_ITEMS_POSITION_QUERY = "{ call TigPubSubQueryItemPosition(?,?,?,?,?) }";

	private DataRepository data_repo;
	@ConfigField(desc = "Add entry to MAM repository", alias = "mam-add-item-query")
	private String mamAddItem = "{ call TigPubSubMamAddItem(?,?,?,?,?) }";
	@ConfigField(desc = "Find item position in result set from repository", alias = "mam-query-item-position-query")
	private String mamQueryItemPosition = "{ call TigPubSubMamQueryItemPosition(?,?,?,?) }";
	@ConfigField(desc = "Retrieve items from repository", alias = "mam-query-items-query")
	private String mamQueryItems = "{ call TigPubSubMamQueryItems(?,?,?,?,?) }";
	@ConfigField(desc = "Count number of items from repository", alias = "mam-query-items-count-query")
	private String mamQueryItemsCount = "{ call TigPubSubMamQueryItemsCount(?,?,?) }";
	private LinkedBlockingDeque<HashCode> pool_hashCodes = new LinkedBlockingDeque<>();

	public PubSubDAOJDBC() {
	}

	@Override
	public Long createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
						   NodeType nodeType, Long collectionId, boolean autocreateService) throws RepositoryException {
		Long nodeId = null;
		HashCode hash = null;
		try {
			ResultSet rs = null;
			String serializedNodeConfig = null;
			if (nodeConfig != null) {
				nodeConfig.setNodeType(nodeType);
				serializedNodeConfig = nodeConfig.getFormElement().toString();
			}

			hash = takeDao();
			PreparedStatement create_node_sp = data_repo.getPreparedStatement(hash.hashCode(), CREATE_NODE_QUERY);
			synchronized (create_node_sp) {
				try {
					create_node_sp.setString(1, serviceJid.toString());
					create_node_sp.setString(2, nodeName);
					create_node_sp.setInt(3, nodeType.ordinal());
					create_node_sp.setString(4, ownerJid.toString());
					create_node_sp.setString(5, serializedNodeConfig);
					if (collectionId == null) {
						create_node_sp.setNull(6, java.sql.Types.BIGINT);
					} else {
						create_node_sp.setLong(6, collectionId);
					}
					data_repo.setTimestamp(create_node_sp, 7, new Timestamp(System.currentTimeMillis()));
					create_node_sp.setString(8, serviceJid.getDomain());
					create_node_sp.setInt(9, autocreateService ? 1 : 0);

					switch (this.data_repo.getDatabaseType()) {
						case sqlserver:
						case jtds:
							create_node_sp.executeUpdate();
							return getNodeId(serviceJid, nodeName);

						default:
							rs = create_node_sp.executeQuery();
							break;
					}

					if (rs.next()) {
						nodeId = rs.getLong(1);
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new RepositoryException("Error while adding node to repository, already exists?", e);
		} catch (SQLException e) {
			log.log(Level.FINE, "Error creating node", e);
			throw new RepositoryException("Problem accessing repository.", e);
		} finally {
			if (hash != null) {
				offerDao(hash);
			}
		}

		return nodeId;
	}

	@Override
	public void createService(BareJID serviceJID, boolean isPublic) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "creating service: serviceJid: {0}, component: {1}, public: {2}",
					new Object[]{serviceJID, serviceJID.getDomain(), isPublic});
		}
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement createServiceStmt = data_repo.getPreparedStatement(hash.hashCode(), CREATE_SERVICE_QUERY);
			synchronized (createServiceStmt) {
				createServiceStmt.setString(1, serviceJID.toString());
				createServiceStmt.setString(2, serviceJID.getDomain());
				createServiceStmt.setInt(3, isPublic ? 1 : 0);
				createServiceStmt.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item removing error", e);
		} finally {
			if (hash != null) {
				offerDao(hash);
			}
		}

	}

	@Override
	public List<BareJID> getServices(BareJID domain, Boolean isPublic) throws RepositoryException {
		HashCode hash = null;
		try {
			ResultSet rs;
			hash = takeDao();
			PreparedStatement getServicesStmt = data_repo.getPreparedStatement(hash.hashCode(), GET_SERVICES_QUERY);
			synchronized (getServicesStmt) {
				try {
					getServicesStmt.setString(1, domain.toString());
					if (isPublic != null) {
						getServicesStmt.setInt(2, isPublic ? 1 : 0);
					} else {
						getServicesStmt.setNull(2, Types.INTEGER);
					}

					rs = getServicesStmt.executeQuery();
					List<BareJID> results = new ArrayList<>();
					while (rs.next()) {
						results.add(BareJID.bareJIDInstanceNS(rs.getString(1)));
					}
					return results;
				} finally {
					if (hash != null) {
						offerDao(hash);
					}
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item removing error", e);
		} finally {
			if (hash != null) {
				offerDao(hash);
			}
		}
	}

	@Override
	public void deleteItem(BareJID serviceJid, Long nodeId, String id) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "deleting Item: serviceJid: {0}, nodeId: {1}, id: {2}",
					new Object[]{serviceJid, nodeId, id});
		}
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement delete_item_sp = data_repo.getPreparedStatement(hash.hashCode(), DELETE_ITEM_QUERY);
			synchronized (delete_item_sp) {
				delete_item_sp.setLong(1, nodeId);
				delete_item_sp.setString(2, id);
				delete_item_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item removing error", e);
		} finally {
			if (hash != null) {
				offerDao(hash);
			}
		}
	}

	@Override
	public void deleteNode(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "deleting Node: serviceJid: {0}, nodeId: {1}", new Object[]{serviceJid, nodeId});
		}
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement remove_node_sp = data_repo.getPreparedStatement(hash.hashCode(), REMOVE_NODE_QUERY);
			synchronized (remove_node_sp) {
				remove_node_sp.setLong(1, nodeId);
				remove_node_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node deleting error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public String[] getAllNodesList(BareJID serviceJid) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "get all nodes list: serviceJid: {0}", new Object[]{serviceJid});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_all_nodes_sp = data_repo.getPreparedStatement(hash.hashCode(), GET_ALL_NODES_QUERY);
			synchronized (get_all_nodes_sp) {
				try {
					get_all_nodes_sp.setString(1, serviceJid.toString());
					rs = get_all_nodes_sp.executeQuery();
					List<String> names = new ArrayList<String>();
					while (rs.next()) {
						names.add(rs.getString(1));
					}
					return names.toArray(new String[0]);
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Nodes list getting error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException {
		return getNodesList(serviceJid, nodeName);
	}

	@Override
	public IItems.IItem getItem(BareJID serviceJid, Long nodeId, String id) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting string from item: serviceJid: {0}, nodeId: {1}",
					new Object[]{serviceJid, nodeId});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_item_sp = data_repo.getPreparedStatement(hash.hashCode(), GET_ITEM_QUERY);
			synchronized (get_item_sp) {
				try {
					get_item_sp.setLong(1, nodeId);
					get_item_sp.setString(2, id);
					rs = get_item_sp.executeQuery();
					if (rs.next()) {
						Element item = itemDataToElement(rs.getString(1));
						String node = rs.getString(2);
						String uuid = rs.getString(3);
						return new IItems.Item(node, id, uuid, item);
					}
					return null;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Could not load item reading error", e);
		} finally {
			offerDao(hash);
		}
	}
	
	@Override
	public String[] getItemsIds(BareJID serviceJid, Long nodeId, CollectionItemsOrdering order) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getting items IDs: serviceJid: {0}, nodeId: {1}", new Object[]{serviceJid, nodeId});
		}
		if (null != nodeId) {
			HashCode hash = null;
			try {
				ResultSet rs = null;
				hash = takeDao();
				PreparedStatement get_node_items_ids_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						 GET_NODE_ITEM_IDS_QUERY);
				synchronized (get_node_items_ids_sp) {
					try {
						get_node_items_ids_sp.setLong(1, nodeId);
						get_node_items_ids_sp.setInt(2, order.value());
						rs = get_node_items_ids_sp.executeQuery();
						List<String> ids = new ArrayList<String>();
						while (rs.next()) {
							ids.add(rs.getString(1));
						}
						return ids.toArray(new String[ids.size()]);
					} finally {
						release(null, rs);
					}
				}
			} catch (SQLException e) {
				throw new RepositoryException("Items list reading error", e);
			} finally {
				offerDao(hash);
			}
		} else {
			return null;
		}
	}

	@Override
	public String[] getItemsIdsSince(BareJID serviceJid, Long nodeId, CollectionItemsOrdering order, Date since) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting items since: serviceJid: {0}, nodeId: {1}, since: {2}",
					new Object[]{serviceJid, nodeId, since});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			Timestamp sinceTs = new Timestamp(since.getTime());
			hash = takeDao();
			PreparedStatement get_node_items_ids_since_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						   GET_NODE_ITEM_IDS_SINCE_QUERY);
			synchronized (get_node_items_ids_since_sp) {
				try {
					get_node_items_ids_since_sp.setLong(1, nodeId);
					get_node_items_ids_since_sp.setInt(2, order.value());
					data_repo.setTimestamp(get_node_items_ids_since_sp, 3, sinceTs);
					rs = get_node_items_ids_since_sp.executeQuery();
					List<String> ids = new ArrayList<String>();
					while (rs.next()) {
						ids.add(rs.getString(1));
					}
					return ids.toArray(new String[ids.size()]);
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Items list reading error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public List<IItems.IItem> getItems(BareJID serviceJid, List<Long> nodesIds, Date afterDate, Date beforeDate, RSM rsm, CollectionItemsOrdering ordering)
			throws RepositoryException {
		int count = 0;

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nodesIds.size(); i++) {
			if (i != 0) {
				sb.append(',');
			}
			sb.append(nodesIds.get(i).longValue());
		}
		String ids = sb.toString();
		
		Integer after = null;
		Integer before = null;


		try {
			PreparedStatement st = data_repo.getPreparedStatement(ids.hashCode(), COUNT_NODES_ITEMS_QUERY);
			synchronized (st) {
				ResultSet rs = null;
				try {
					int i =1;
					st.setString(i++, ids);
					data_repo.setTimestamp(st, i++, afterDate == null ? null : new Timestamp(afterDate.getTime()));
					data_repo.setTimestamp(st, i++, beforeDate == null ? null : new Timestamp(beforeDate.getTime()));
					st.setInt(i++, ordering.value());
					rs = st.executeQuery();
					if (rs.next()) {
						count = rs.getInt(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}

			if (rsm.getAfter() != null) {
				st = data_repo.getPreparedStatement(ids.hashCode(), GET_NODES_ITEMS_POSITION_QUERY);
				synchronized (st) {
					ResultSet rs = null;
					try {
						int i = 1;
						st.setString(i++, ids);
						data_repo.setTimestamp(st, i++, afterDate == null ? null : new Timestamp(afterDate.getTime()));
						data_repo.setTimestamp(st, i++,
											   beforeDate == null ? null : new Timestamp(beforeDate.getTime()));
						st.setInt(i++, ordering.value());
						st.setString(i++, rsm.getAfter());
						rs = st.executeQuery();
						if (rs.next()) {
							after = rs.getInt(1);
						}
					} finally {
						data_repo.release(null, rs);
					}
				}
			}

			if (rsm.getBefore() != null) {
				st = data_repo.getPreparedStatement(ids.hashCode(), GET_NODES_ITEMS_POSITION_QUERY);
				synchronized (st) {
					ResultSet rs = null;
					try {
						int i = 1;
						st.setString(i++, ids);
						data_repo.setTimestamp(st, i++, afterDate == null ? null : new Timestamp(afterDate.getTime()));
						data_repo.setTimestamp(st, i++,
											   beforeDate == null ? null : new Timestamp(beforeDate.getTime()));
						st.setInt(i++, ordering.value());
						st.setString(i++, rsm.getBefore());
						rs = st.executeQuery();
						if (rs.next()) {
							before = rs.getInt(1);
						}
					} finally {
						data_repo.release(null, rs);
					}
				}
			}
			
			calculateOffsetAndPosition(rsm, count, before, after);

			st = data_repo.getPreparedStatement(ids.hashCode(), GET_NODES_ITEMS_QUERY);

			List<IItems.IItem> results = new ArrayList<>();
			synchronized (st) {
				ResultSet rs = null;
				try {
					int i = 1;
					st.setString(i++, ids);
					data_repo.setTimestamp(st, i++, afterDate == null ? null : new Timestamp(afterDate.getTime()));
					data_repo.setTimestamp(st, i++,
										   beforeDate == null ? null : new Timestamp(beforeDate.getTime()));
					st.setInt(i++, ordering.value());
					st.setInt(i++, rsm.getMax());
					st.setInt(i++, rsm.getIndex());

					rs = st.executeQuery();

					while (rs.next()) {
						String node = rs.getString(1);
						String itemId = rs.getString(2);
						String itemUuid = rs.getString(3);
						Element itemEl = itemDataToElement(rs.getString(5));

						results.add(new IItems.Item(node, itemId, itemUuid, itemEl));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
			if (results.size() > 0) {
				rsm.setLast(results.get(results.size() - 1).getId());
				rsm.setFirst(results.get(0).getId());
			}
			return results;
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve items", ex);
		}
	}

	@Override
	public List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, Long nodeId, String nodeName)
			throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting items meta: serviceJid: {0}, nodeId: {1}, nodeName: {2}",
					new Object[]{serviceJid, nodeId, nodeName});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_node_items_meta_sp = data_repo.getPreparedStatement(hash.hashCode(),
																					  GET_NODE_ITEMS_META_QUERY);
			synchronized (get_node_items_meta_sp) {
				try {
					get_node_items_meta_sp.setLong(1, nodeId);
					rs = get_node_items_meta_sp.executeQuery();
					List<IItems.ItemMeta> results = new ArrayList<IItems.ItemMeta>();
					while (rs.next()) {
						String id = rs.getString(1);
						Date creationDate = data_repo.getTimestamp(rs, 2);
						Date updateDate = data_repo.getTimestamp(rs, 3);
						String uuid = rs.getString(4);
						results.add(new IItems.ItemMeta(nodeName, id, creationDate, updateDate, uuid));
					}
					return results;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Items list reading error", e);
		} finally {
			offerDao(hash);
		}
	}
	
	@Override
	public Map<BareJID, UsersAffiliation> getNodeAffiliations(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node affiliation: serviceJid: {0}, nodeId: {1}",
					new Object[]{serviceJid, nodeId});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_node_affiliations_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						GET_NODE_AFFILIATIONS_QUERY);
			synchronized (get_node_affiliations_sp) {
				try {
					get_node_affiliations_sp.setLong(1, nodeId);
					rs = get_node_affiliations_sp.executeQuery();
					Map<BareJID, UsersAffiliation> data = new HashMap<>();

					while (rs.next()) {
						BareJID jid = BareJID.bareJIDInstanceNS(rs.getString(1));
						Affiliation affil = Affiliation.valueOf(rs.getString(2));
						data.put(jid, new UsersAffiliation(jid, affil));
					}
					return data;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers reading error", e);
		} finally {
			offerDao(hash);
		}
	}

	private Long getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting Node ID: serviceJid: {0}, nodeName: {1}",
					new Object[]{serviceJid, nodeName});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_node_id_sp = data_repo.getPreparedStatement(hash.hashCode(), GET_NODE_ID_QUERY);
			synchronized (get_node_id_sp) {
				try {
					get_node_id_sp.setString(1, serviceJid.toString());
					get_node_id_sp.setString(2, nodeName);
					rs = get_node_id_sp.executeQuery();
					if (rs.next()) {
						final long nodeId = rs.getLong(1);
						if (log.isLoggable(Level.FINEST)) {
							log.log(Level.FINEST,
									"Getting Node ID: serviceJid: {0}, nodeName: {1}, nodeId: {2}, get_node_id_sp: {3}",
									new Object[]{serviceJid, nodeName, nodeId, get_node_id_sp});
						}
						return nodeId;
					}
					return null;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Retrieving node id error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public NodeMeta<Long> getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting Node ID: serviceJid: {0}, nodeName: {1}",
					new Object[]{serviceJid, nodeName});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_node_meta_sp = data_repo.getPreparedStatement(hash.hashCode(), GET_NODE_META_QUERY);
			synchronized (get_node_meta_sp) {
				try {
					get_node_meta_sp.setString(1, serviceJid.toString());
					get_node_meta_sp.setString(2, nodeName);
					rs = get_node_meta_sp.executeQuery();
					if (rs.next()) {
						final long nodeId = rs.getLong(1);
						final String configStr = rs.getString(2);
						final String creator = rs.getString(3);
						final Date creationTime = data_repo.getTimestamp(rs, 4);
						final NodeMeta<Long> nodeMeta = new NodeMeta(nodeId, parseConfig(nodeName, configStr),
																	 creator != null
																	 ? BareJID.bareJIDInstance(creator)
																	 : null, creationTime);

						if (log.isLoggable(Level.FINEST)) {
							log.log(Level.FINEST,
									"Getting Node ID: serviceJid: {0}, nodeName: {1}, nodeId: {2}, get_node_id_sp: {3}, nodeMeta: {4}",
									new Object[]{serviceJid, nodeName, nodeId, GET_NODE_META_QUERY, nodeMeta});
						}
						return nodeMeta;
					}
					return null;
				} finally {
					release(null, rs);
				}
			}
		} catch (TigaseStringprepException | SQLException e) {
			throw new RepositoryException("Retrieving node meta data error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting nodes list: serviceJid: {0}, nodeName: {1}",
					new Object[]{serviceJid, nodeName});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			if (nodeName == null) {
				PreparedStatement get_root_nodes_sp = data_repo.getPreparedStatement(hash.hashCode(),
																					 GET_ROOT_NODES_QUERY);
				synchronized (get_root_nodes_sp) {
					try {
						get_root_nodes_sp.setString(1, serviceJid.toString());
						rs = get_root_nodes_sp.executeQuery();
						List<String> names = new ArrayList<String>();
						while (rs.next()) {
							names.add(rs.getString(1));
						}
						return names.toArray(new String[0]);
					} finally {
						release(null, rs);
					}
				}
			} else {
				PreparedStatement get_child_nodes_sp = data_repo.getPreparedStatement(hash.hashCode(),
																					  GET_CHILD_NODES_QUERY);
				synchronized (get_child_nodes_sp) {
					try {
						get_child_nodes_sp.setString(1, serviceJid.toString());
						get_child_nodes_sp.setString(2, nodeName);
						rs = get_child_nodes_sp.executeQuery();
						List<String> names = new ArrayList<String>();
						while (rs.next()) {
							names.add(rs.getString(1));
						}
						return names.toArray(new String[0]);
					} finally {
						release(null, rs);
					}
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Nodes list getting error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public long getNodesCount(BareJID serviceJid) throws RepositoryException {
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();

			PreparedStatement stmt = data_repo.getPreparedStatement(hash.hashCode(), COUNT_NODES_QUERY);

			synchronized (stmt) {
				try {
					stmt.setString(1, serviceJid == null ? null : serviceJid.toString());
					rs = stmt.executeQuery();
					if (rs.next()) {
						return rs.getLong(1);
					} else {
						return 0;
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException ex) {
			throw new RepositoryException("Counting nodes error", ex);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public Map<BareJID, UsersSubscription> getNodeSubscriptions(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node subscriptions: serviceJid: {0}, nodeId: {1}",
					new Object[]{serviceJid, nodeId});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_node_subscriptions_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						 GET_NODE_SUBSCRIPTIONS_QUERY);
			synchronized (get_node_subscriptions_sp) {
				try {
					get_node_subscriptions_sp.setLong(1, nodeId);
					rs = get_node_subscriptions_sp.executeQuery();
					Map<BareJID, UsersSubscription> data = new HashMap<>();
					while (rs.next()) {
						BareJID jid = BareJID.bareJIDInstanceNS(rs.getString(1));
						Subscription subscr = Subscription.valueOf(rs.getString(2));
						String subscrId = rs.getString(3);
						data.put(jid, new UsersSubscription(jid, subscrId, subscr));
					}
					return data;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers reading error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid)
			throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting user affiliation: serviceJid: {0}, jid: {1}", new Object[]{serviceJid, jid});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			Map<String, UsersAffiliation> result = new HashMap<String, UsersAffiliation>();
			hash = takeDao();
			PreparedStatement get_user_affiliations_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						GET_USER_AFFILIATIONS_QUERY);
			synchronized (get_user_affiliations_sp) {
				try {
					get_user_affiliations_sp.setString(1, serviceJid.toString());
					get_user_affiliations_sp.setString(2, jid.toString());
					rs = get_user_affiliations_sp.executeQuery();
					while (rs.next()) {
						String nodeName = rs.getString(1);
						Affiliation affil = Affiliation.valueOf(rs.getString(2));
						result.put(nodeName, new UsersAffiliation(jid, affil));
					}
				} finally {
					release(null, rs);
				}
			}
			return result;
		} catch (SQLException e) {
			throw new RepositoryException("User affiliations reading error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid)
			throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting user subs: serviceJid: {0}, jid: {1}", new Object[]{serviceJid, jid});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_user_subscriptions_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						 GET_USER_SUBSCRIPTIONS_QUERY);
			Map<String, UsersSubscription> result = new HashMap<String, UsersSubscription>();
			synchronized (get_user_subscriptions_sp) {
				try {
					get_user_subscriptions_sp.setString(1, serviceJid.toString());
					get_user_subscriptions_sp.setString(2, jid.toString());
					rs = get_user_subscriptions_sp.executeQuery();
					while (rs.next()) {
						String nodeName = rs.getString(1);
						Subscription subscr = Subscription.valueOf(rs.getString(2));
						String subscrId = rs.getString(3);
						result.put(nodeName, new UsersSubscription(jid, subscrId, subscr));
					}
				} finally {
					release(null, rs);
				}
			}
			return result;
		} catch (SQLException e) {
			throw new RepositoryException("User affiliations reading error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public void addMAMItem(BareJID serviceJid, Long nodeId, String uuid, Element message, String itemId) throws RepositoryException {
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement write_item_sp = data_repo.getPreparedStatement(hash.hashCode(), mamAddItem);
			ResultSet rs = null;
			synchronized (write_item_sp) {
				try {
					write_item_sp.setLong(1, nodeId);
					write_item_sp.setString(2, uuid);
					data_repo.setTimestamp(write_item_sp, 3, new Timestamp(System.currentTimeMillis()));
					write_item_sp.setString(4, message.toString());
					write_item_sp.setString(5, itemId);
					write_item_sp.execute();
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item writing error", e);
		} finally {
			offerDao(hash);
		}

	}

	@Override
	public void queryItems(Query query, Long nodeId,
						   MAMRepository.ItemHandler<Query, IPubSubRepository.Item> itemHandler)
			throws RepositoryException, ComponentException {
		Integer count = countMAMItems(query, nodeId);
		if (count == null) {
			count = 0;
		}

		Integer after = getMAMItemPosition(query, nodeId, query.getRsm().getAfter());
		Integer before = getMAMItemPosition(query, nodeId, query.getRsm().getBefore());

		calculateOffsetAndPosition(query.getRsm(), count, before, after);

		try {
			PreparedStatement st = data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(), mamQueryItems);

			synchronized (st) {
				ResultSet rs = null;
				try {
					int i = setStatementParamsForMAM(st, query, nodeId);
					st.setInt(i++, query.getRsm().getMax());
					st.setInt(i++, query.getRsm().getIndex());

					rs = st.executeQuery();

					while (rs.next()) {
						String itemUuid = rs.getString(1);
						Timestamp ts = data_repo.getTimestamp(rs, 2);
						Element itemEl = itemDataToElement(rs.getString(3));

						itemHandler.itemFound(query, new MAMItem(itemUuid, ts, itemEl));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve items", ex);
		}
	}
	
	@Override
	public void removeNodeSubscription(BareJID serviceJid, Long nodeId, BareJID jid) throws RepositoryException {
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement delete_node_subscriptions_sp = data_repo.getPreparedStatement(hash.hashCode(),
																							DELETE_NODE_SUBSCRIPTIONS_QUERY);
			synchronized (delete_node_subscriptions_sp) {
				delete_node_subscriptions_sp.setLong(1, nodeId);
				delete_node_subscriptions_sp.setString(2, jid.toString());
				delete_node_subscriptions_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers fragment removing error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public void deleteService(BareJID serviceJid) throws RepositoryException {
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement remove_service_sp = data_repo.getPreparedStatement(hash.hashCode(), REMOVE_SERVICE_QUERY);
			synchronized (remove_service_sp) {
				remove_service_sp.setString(1, serviceJid.toString());
				remove_service_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers fragment removing error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public void updateNodeAffiliation(BareJID serviceJid, Long nodeId, String nodeName, UsersAffiliation affiliation)
			throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node affiliation[1]: " + nodeName + " / " + affiliation);
		}

		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement set_node_affiliations_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						SET_NODE_AFFILIATION_QUERY);
			synchronized (set_node_affiliations_sp) {
				try {
					set_node_affiliations_sp.setLong(1, nodeId);
					set_node_affiliations_sp.setString(2, affiliation.getJid().toString());
					set_node_affiliations_sp.setString(3, affiliation.getAffiliation().name());
					switch (data_repo.getDatabaseType()) {
						case mysql:
							rs = set_node_affiliations_sp.executeQuery();
							break;
						default:
							set_node_affiliations_sp.execute();
							break;
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers writing error", e);
		} finally {
			offerDao(hash);
		}
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node affiliation[2]");
		}

	}

	@Override
	public void updateNodeConfig(final BareJID serviceJid, final Long nodeId, final String serializedData,
								 final Long collectionId) throws RepositoryException {
		HashCode hash = null;
		try {
			hash = takeDao();
			ResultSet rs = null;
			PreparedStatement set_node_configuration_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						 SET_NODE_CONFIGURATION_QUERY);
			synchronized (set_node_configuration_sp) {
				try {
					set_node_configuration_sp.setLong(1, nodeId);
					set_node_configuration_sp.setString(2, serializedData);
					if (collectionId == null) {
						set_node_configuration_sp.setNull(3, java.sql.Types.BIGINT);
					} else {
						set_node_configuration_sp.setLong(3, collectionId);
					}
					switch (data_repo.getDatabaseType()) {
						case mysql:
							rs = set_node_configuration_sp.executeQuery();
							break;
						default:
							set_node_configuration_sp.execute();
							break;
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node configuration writing error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public void updateNodeSubscription(BareJID serviceJid, Long nodeId, String nodeName, UsersSubscription subscription)
			throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node subscriptions[1]: " + nodeName + " / " + subscription);
		}

		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement set_node_subscriptions_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						 SET_NODE_SUBSCRIPTION_QUERY);
			synchronized (set_node_subscriptions_sp) {
				try {
					set_node_subscriptions_sp.setLong(1, nodeId);
					set_node_subscriptions_sp.setString(2, subscription.getJid().toString());
					set_node_subscriptions_sp.setString(3, subscription.getSubscription().name());
					set_node_subscriptions_sp.setString(4, subscription.getSubid());
					switch (data_repo.getDatabaseType()) {
						case mysql:
							rs = set_node_subscriptions_sp.executeQuery();
							break;
						default:
							set_node_subscriptions_sp.execute();
							break;
					}
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers writing error", e);
		} finally {
			offerDao(hash);
		}
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Updating node subscriptions[2]");
		}

	}

	@Override
	public void writeItem(final BareJID serviceJid, final Long nodeId, long timeInMilis, final String id,
						  final String publisher, final Element item, final String uuid) throws RepositoryException {
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement write_item_sp = data_repo.getPreparedStatement(hash.hashCode(), WRITE_ITEM_QUERY);
			ResultSet rs = null;
			synchronized (write_item_sp) {
				try {
					write_item_sp.setLong(1, nodeId);
					write_item_sp.setString(2, id);
					write_item_sp.setString(3, publisher);
					write_item_sp.setString(4, item.toString());
					data_repo.setTimestamp(write_item_sp, 5, new Timestamp(System.currentTimeMillis()));
					write_item_sp.setString(6, uuid);
					write_item_sp.execute();
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item writing error", e);
		} finally {
			offerDao(hash);
		}
	}

	public void setDataSource(DataRepository dataSource) {
		try {
			initPreparedStatements(dataSource);
		} catch (SQLException ex) {
			new RuntimeException("Failed to initialize access to SQL database for PubSubDAOJDBC", ex);
		}
		this.data_repo = dataSource;
		int poolSize = dataSource.getPoolSize();
		for (int i = 0; i < poolSize; i++) {
			pool_hashCodes.offer(new HashCode(dataSource, i));
		}
	}
	
	protected Integer countMAMItems(Query query, Long nodeId) throws TigaseDBException {
		try {
			PreparedStatement st = this.data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(),
																	   mamQueryItemsCount);
			synchronized (st) {
				ResultSet rs = null;
				try {
					setStatementParamsForMAM(st, query, nodeId);

					rs = st.executeQuery();
					if (rs.next()) {
						return rs.getInt(1);
					} else {
						return null;
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Failed to retrieve number of items for nodes at " + query.getComponentJID(),
										ex);
		}
	}

	protected Integer getMAMItemPosition(Query query, Long nodeId, String itemUuid)
			throws RepositoryException, ComponentException {
		if (itemUuid == null) {
			return null;
		}

		try {
			PreparedStatement st = this.data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(),
																	   mamQueryItemPosition);
			synchronized (st) {
				ResultSet rs = null;
				try {
					int i = setStatementParamsForMAM(st, query, nodeId);
					st.setString(i++, itemUuid);

					rs = st.executeQuery();
					if (rs.next()) {
						return rs.getInt(1) - 1;
					} else {
						throw new ComponentException(Authorization.ITEM_NOT_FOUND,
													 "Not found item with uuid = " + itemUuid);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Can't find position for item with id " + itemUuid + " in archive for room " +
												query.getComponentJID(), ex);
		}
	}

	protected int setStatementParamsForMAM(PreparedStatement st, Query query, Long nodeId) throws SQLException {
		int i = 1;
		st.setLong(i++, nodeId);
		data_repo.setTimestamp(st, i++, query.getStart() == null ? null : new Timestamp(query.getStart().getTime()));
		data_repo.setTimestamp(st, i++, query.getEnd() == null ? null : new Timestamp(query.getEnd().getTime()));

		return i;
	}

	protected HashCode takeDao() {
		try {
			return pool_hashCodes.take();
		} catch (InterruptedException ex) {
			log.log(Level.WARNING, "Couldn't obtain PubSub DAO from the pool", ex);
		}
		return null;
	}

	protected void offerDao(HashCode hash) {
		if (hash != null && hash.canOffer()) {
			pool_hashCodes.offer(hash);
		}
	}

	/**
	 * <code>initPreparedStatements</code> method initializes internal database connection variables such as prepared
	 * statements.
	 *
	 * @throws SQLException if an error occurs on database query.
	 */
	private void initPreparedStatements(DataRepository data_repo) throws SQLException {
		String query;

		data_repo.initPreparedStatement(CREATE_NODE_QUERY, CREATE_NODE_QUERY);
		data_repo.initPreparedStatement(REMOVE_NODE_QUERY, REMOVE_NODE_QUERY);
		data_repo.initPreparedStatement(CREATE_SERVICE_QUERY,CREATE_SERVICE_QUERY);
		data_repo.initPreparedStatement(REMOVE_SERVICE_QUERY, REMOVE_SERVICE_QUERY);
		data_repo.initPreparedStatement(GET_SERVICES_QUERY, GET_SERVICES_QUERY);
		data_repo.initPreparedStatement(GET_NODE_ID_QUERY, GET_NODE_ID_QUERY);
		data_repo.initPreparedStatement(GET_NODE_META_QUERY, GET_NODE_META_QUERY);
		data_repo.initPreparedStatement(GET_ITEM_QUERY, GET_ITEM_QUERY);
		data_repo.initPreparedStatement(WRITE_ITEM_QUERY, WRITE_ITEM_QUERY);
		data_repo.initPreparedStatement(DELETE_ITEM_QUERY, DELETE_ITEM_QUERY);
		data_repo.initPreparedStatement(GET_NODE_ITEM_IDS_QUERY, GET_NODE_ITEM_IDS_QUERY);
		data_repo.initPreparedStatement(GET_NODE_ITEM_IDS_SINCE_QUERY, GET_NODE_ITEM_IDS_SINCE_QUERY);
		data_repo.initPreparedStatement(GET_NODE_ITEMS_META_QUERY, GET_NODE_ITEMS_META_QUERY);
		data_repo.initPreparedStatement(COUNT_NODES_QUERY, COUNT_NODES_QUERY);
		data_repo.initPreparedStatement(GET_ALL_NODES_QUERY, GET_ALL_NODES_QUERY);
		data_repo.initPreparedStatement(GET_ROOT_NODES_QUERY, GET_ROOT_NODES_QUERY);
		data_repo.initPreparedStatement(GET_CHILD_NODES_QUERY, GET_CHILD_NODES_QUERY);
		data_repo.initPreparedStatement(SET_NODE_CONFIGURATION_QUERY, SET_NODE_CONFIGURATION_QUERY);
		data_repo.initPreparedStatement(SET_NODE_AFFILIATION_QUERY, SET_NODE_AFFILIATION_QUERY);
		data_repo.initPreparedStatement(GET_NODE_AFFILIATIONS_QUERY, GET_NODE_AFFILIATIONS_QUERY);
		data_repo.initPreparedStatement(GET_NODE_SUBSCRIPTIONS_QUERY, GET_NODE_SUBSCRIPTIONS_QUERY);
		data_repo.initPreparedStatement(SET_NODE_SUBSCRIPTION_QUERY, SET_NODE_SUBSCRIPTION_QUERY);
		data_repo.initPreparedStatement(DELETE_NODE_SUBSCRIPTIONS_QUERY, DELETE_NODE_SUBSCRIPTIONS_QUERY);
		data_repo.initPreparedStatement(GET_USER_AFFILIATIONS_QUERY, GET_USER_AFFILIATIONS_QUERY);
		data_repo.initPreparedStatement(GET_USER_SUBSCRIPTIONS_QUERY, GET_USER_SUBSCRIPTIONS_QUERY);

		data_repo.initPreparedStatement(COUNT_NODES_ITEMS_QUERY, COUNT_NODES_ITEMS_QUERY);
		data_repo.initPreparedStatement(GET_NODES_ITEMS_QUERY, GET_NODES_ITEMS_QUERY);
		data_repo.initPreparedStatement(GET_NODES_ITEMS_POSITION_QUERY, GET_NODES_ITEMS_POSITION_QUERY);

		data_repo.initPreparedStatement(mamAddItem, mamAddItem);
		data_repo.initPreparedStatement(mamQueryItems, mamQueryItems);
		data_repo.initPreparedStatement(mamQueryItemPosition, mamQueryItemPosition);
		data_repo.initPreparedStatement(mamQueryItemsCount, mamQueryItemsCount);
	}

	private void release(Statement stmt, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException sqlEx) {
			}
		}
		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException sqlEx) {
			}
		}
	}

	private class HashCode {

		private final int hash;
		private final int repoHash;

		public HashCode(DataRepository dataRepository, int connNo) {
			hash = connNo;
			repoHash = dataRepository.hashCode();
		}

		@Override
		public int hashCode() {
			return hash;
		}

		public boolean canOffer() {
			return PubSubDAOJDBC.this.data_repo.hashCode() == repoHash;
		}
	}
}
