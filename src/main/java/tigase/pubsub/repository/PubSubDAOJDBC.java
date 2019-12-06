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
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.Subscription;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.stateless.NodeMeta;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.mam.MAMRepository;

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

	private static final String CREATE_NODE_QUERY = "{ call TigPubSubCreateNode(?, ?, ?, ?, ?, ?, ?) }";
	private static final String REMOVE_NODE_QUERY = "{ call TigPubSubRemoveNode(?) }";
	private static final String REMOVE_SERVICE_QUERY = "{ call TigPubSubRemoveService(?) }";
	private static final String GET_NODE_ID_QUERY = "{ call TigPubSubGetNodeId(?, ?) }";
	private static final String GET_NODE_META_QUERY = "{ call TigPubSubGetNodeMeta(?, ?) }";
	private static final String GET_ITEM_QUERY = "{ call TigPubSubGetItem(?, ?) }";
	private static final String WRITE_ITEM_QUERY = "{ call TigPubSubWriteItem(?, ?, ?, ?, ?) }";
	private static final String DELETE_ITEM_QUERY = "{ call TigPubSubDeleteItem(?, ?) }";
	private static final String GET_NODE_ITEM_IDS_QUERY = "{ call TigPubSubGetNodeItemsIds(?) }";
	private static final String GET_NODE_ITEM_IDS_SINCE_QUERY = "{ call TigPubSubGetNodeItemsIdsSince(?,?) }";
	private static final String GET_NODE_ITEMS_META_QUERY = "{ call TigPubSubGetNodeItemsMeta(?) }";
	private static final String COUNT_NODES_QUERY = "{ call TigPubSubCountNodes(?) }";
	private static final String GET_ALL_NODES_QUERY = "{ call TigPubSubGetAllNodes(?) }";
	private static final String GET_ROOT_NODES_QUERY = "{ call TigPubSubGetRootNodes(?) }";
	private static final String GET_CHILD_NODES_QUERY = "{ call TigPubSubGetChildNodes(?,?) }";
	private static final String DELETE_ALL_NODES_QUERY = "{ call TigPubSubDeleteAllNodes(?) }";
	private static final String SET_NODE_CONFIGURATION_QUERY = "{ call TigPubSubSetNodeConfiguration(?, ?, ?) }";
	private static final String SET_NODE_AFFILIATION_QUERY = "{ call TigPubSubSetNodeAffiliation(?, ?, ?) }";
	private static final String GET_NODE_CONFIGURATION_QUERY = "{ call TigPubSubGetNodeConfiguration(?) }";
	private static final String GET_NODE_AFFILIATIONS_QUERY = "{ call TigPubSubGetNodeAffiliations(?) }";
	private static final String GET_NODE_SUBSCRIPTIONS_QUERY = "{ call TigPubSubGetNodeSubscriptions(?) }";
	private static final String SET_NODE_SUBSCRIPTION_QUERY = "{ call TigPubSubSetNodeSubscription(?, ?, ?, ?) }";
	private static final String DELETE_NODE_SUBSCRIPTIONS_QUERY = "{ call TigPubSubDeleteNodeSubscription(?, ?) }";
	private static final String GET_USER_AFFILIATIONS_QUERY = "{ call TigPubSubGetUserAffiliations(?, ?) }";
	private static final String GET_USER_SUBSCRIPTIONS_QUERY = "{ call TigPubSubGetUserSubscriptions(?, ?) }";
	private DataRepository data_repo;
	@ConfigField(desc = "Find item position in result set from repository", alias = "mam-query-item-position-query")
	private String mamQueryItemPosition = "{ call TigPubSubMamQueryItemPosition(?,?,?,?,?,?,?) }";
	@ConfigField(desc = "Retrieve items from repository", alias = "mam-query-items-query")
	private String mamQueryItems = "{ call TigPubSubMamQueryItems(?,?,?,?,?,?,?) }";
	@ConfigField(desc = "Count number of items from repository", alias = "mam-query-items-count-query")
	private String mamQueryItemsCount = "{ call TigPubSubMamQueryItemsCount(?,?,?,?,?) }";
	private LinkedBlockingDeque<HashCode> pool_hashCodes = new LinkedBlockingDeque<>();

	public PubSubDAOJDBC() {
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		// TODO
		// We do not support collections yet, so all nodes are in root
		// collection.
		return;
	}

//	private void checkSchema() {
//		if (schemaOk)
//			return;
//
//		try {
//			CallableStatement testCall = conn.prepareCall("{ call TigPubSubGetNodeMeta(?,?) }");
//			testCall.setString(1, "tigase-pubsub");
//			testCall.setString(2, "tigase-pubsub");
//			testCall.execute();
//			testCall.close();
//			schemaOk = true;
//		} catch (Exception ex) {
//			String[] msg = {
//					"",
//					"  ---------------------------------------------",
//					"  ERROR! Terminating the server process.",
//					"  PubSub Component is not compatible with",
//					"  database schema which exists in",
//					"  " + db_conn,
//					"  This component uses newer schema. To continue",
//					"  use of currently deployed schema, please use",
//					"  older version of PubSub Component.",
//					"  To convert database to new schema please see:",
//					"  https://projects.tigase.org/projects/tigase-pubsub/wiki/PubSub_database_schema_conversion"
//			};
//			if (XMPPServer.isOSGi()) {
//				// for some reason System.out.println is not working in OSGi
//				for (String line : msg) {
//					log.log(Level.SEVERE, line);
//				}
//			}
//			else {
//				for (String line : msg) {
//					System.out.println(line);
//				}
//			}
//			log.log(Level.FINEST, "Exception during checkSchema: ", ex);
//
//			System.exit(1);
//		}
//	}

	@Override
	public Long createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
						   NodeType nodeType, Long collectionId) throws RepositoryException {
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
	public Element getItem(BareJID serviceJid, Long nodeId, String id) throws RepositoryException {
		String data = getStringFromItem(serviceJid, nodeId, id, 1);
		if (data == null) {
			return null;
		}
		return itemDataToElement(data.toCharArray());
	}

	// @Override
	// public String getItemPublisher( BareJID serviceJid, long nodeId, String
	// id ) throws RepositoryException {
	// return getStringFromItem( serviceJid, nodeId, id, 2 );
	// }

	@Override
	public Date getItemCreationDate(final BareJID serviceJid, final Long nodeId, final String id)
			throws RepositoryException {
		return getDateFromItem(serviceJid, nodeId, id, 3);
	}

	@Override
	public String[] getItemsIds(BareJID serviceJid, Long nodeId) throws RepositoryException {
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
	public String[] getItemsIdsSince(BareJID serviceJid, Long nodeId, Date since) throws RepositoryException {
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
					data_repo.setTimestamp(get_node_items_ids_since_sp, 2, sinceTs);
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
						results.add(new IItems.ItemMeta(nodeName, id, creationDate, updateDate));
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
	public Date getItemUpdateDate(BareJID serviceJid, Long nodeId, String id) throws RepositoryException {
		return getDateFromItem(serviceJid, nodeId, id, 4);
	}

	@Override
	public NodeAffiliations getNodeAffiliations(BareJID serviceJid, Long nodeId) throws RepositoryException {
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
					ArrayDeque<UsersAffiliation> data = new ArrayDeque<UsersAffiliation>();
					while (rs.next()) {
						BareJID jid = BareJID.bareJIDInstanceNS(rs.getString(1));
						Affiliation affil = Affiliation.valueOf(rs.getString(2));
						data.offer(new UsersAffiliation(jid, affil));
					}
					return NodeAffiliations.create(data);
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
	public String getNodeConfig(BareJID serviceJid, Long nodeId) throws RepositoryException {
		return readNodeConfigFormData(serviceJid, nodeId);
	}

	@Override
	public Long getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException {
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
	public NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, Long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting node subscriptions: serviceJid: {0}, nodeId: {1}",
					new Object[]{serviceJid, nodeId});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			final NodeSubscriptions ns = NodeSubscriptions.create();
			hash = takeDao();
			PreparedStatement get_node_subscriptions_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						 GET_NODE_SUBSCRIPTIONS_QUERY);
			synchronized (get_node_subscriptions_sp) {
				try {
					get_node_subscriptions_sp.setLong(1, nodeId);
					rs = get_node_subscriptions_sp.executeQuery();
					ArrayDeque<UsersSubscription> data = new ArrayDeque<UsersSubscription>();
					while (rs.next()) {
						BareJID jid = BareJID.bareJIDInstanceNS(rs.getString(1));
						Subscription subscr = Subscription.valueOf(rs.getString(2));
						String subscrId = rs.getString(3);
						data.offer(new UsersSubscription(jid, subscrId, subscr));
					}
					ns.init(data);
					return ns;
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
	public void queryItems(Query query, List<Long> nodesIds,
						   MAMRepository.ItemHandler<Query, IPubSubRepository.Item> itemHandler)
			throws RepositoryException, ComponentException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nodesIds.size(); i++) {
			if (i != 0) {
				sb.append(',');
			}
			sb.append(nodesIds.get(i).longValue());
		}

		String ids = sb.toString();

		Integer count = countItems(query, ids);
		if (count == null) {
			count = 0;
		}

		Integer after = getItemPosition(query, ids, query.getRsm().getAfter());
		Integer before = getItemPosition(query, ids, query.getRsm().getBefore());

		calculateOffsetAndPosition(query, count, before, after);

		try {
			PreparedStatement st = data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(), mamQueryItems);

			synchronized (st) {
				ResultSet rs = null;
				try {
					int i = setStatementParamsForMAM(st, query, ids);
					st.setInt(i++, query.getRsm().getMax());
					st.setInt(i++, query.getRsm().getIndex());

					rs = st.executeQuery();

					while (rs.next()) {
						String node = rs.getString(1);
						long nodeId = rs.getLong(2);
						String itemId = rs.getString(3);
						Timestamp creationDate = data_repo.getTimestamp(rs, 4);
						Element itemEl = itemDataToElement(rs.getString(5));

						itemHandler.itemFound(query, new Item(node, nodeId, itemId, creationDate, itemEl));
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
	public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException {
		// TODO check it
		HashCode hash = null;
		try {
			hash = takeDao();
			PreparedStatement delete_all_nodes_sp = data_repo.getPreparedStatement(hash.hashCode(),
																				   DELETE_ALL_NODES_QUERY);
			synchronized (delete_all_nodes_sp) {
				delete_all_nodes_sp.setString(1, serviceJid.toString());
				delete_all_nodes_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Removing root collection error", e);
		} finally {
			offerDao(hash);
		}
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, Long nodeId) throws RepositoryException {
		// TODO check it
		deleteNode(serviceJid, nodeId);
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
	public void removeService(BareJID serviceJid) throws RepositoryException {
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
						  final String publisher, final Element item) throws RepositoryException {
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

	protected Date getDateFromItem(BareJID serviceJid, long nodeId, String id, int field) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getting date from item: serviceJid: {0}, nodeId: {1}, id: {2}, field: {3}",
					new Object[]{serviceJid, nodeId, id, field});
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
						// String date = rs.getString( field );
						// if ( date == null ) {
						// return null;
						// }
						// -- why do we need this?
						// return DateFormat.getDateInstance().parse( date );
						return data_repo.getTimestamp(rs, field);
					}
				} finally {
					release(null, rs);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item field " + field + " reading error", e);
			// } catch ( ParseException e ) {
			// throw new RepositoryException( "Item field " + field + " parsing
			// error", e );
		} finally {
			offerDao(hash);
		}
	}

	protected String getStringFromItem(BareJID serviceJid, long nodeId, String id, int field)
			throws RepositoryException {
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
						return rs.getString(field);
					}
					return null;
				} finally {
					release(null, rs);
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item field " + field + " reading error", e);
		} finally {
			offerDao(hash);
		}
	}

	protected Integer countItems(Query query, String nodeIds) throws TigaseDBException {
		try {
			PreparedStatement st = this.data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(),
																	   mamQueryItemsCount);
			synchronized (st) {
				ResultSet rs = null;
				try {
					setStatementParamsForMAM(st, query, nodeIds);

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

	protected Integer getItemPosition(Query query, String nodeIds, String itemId)
			throws RepositoryException, ComponentException {
		if (itemId == null) {
			return null;
		}

		try {
			String[] parts = itemId.split(",");
			if (parts.length != 2) {
				throw new ComponentException(Authorization.ITEM_NOT_FOUND, "Not found item with id = " + itemId);
			}
			long itemNodeId = Long.parseLong(parts[0]);
			String id = parts[1];

			PreparedStatement st = this.data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(),
																	   mamQueryItemPosition);
			synchronized (st) {
				ResultSet rs = null;
				try {
					int i = setStatementParamsForMAM(st, query, nodeIds);
					st.setLong(i++, itemNodeId);
					st.setString(i++, id);

					rs = st.executeQuery();
					if (rs.next()) {
						return rs.getInt(1) - 1;
					} else {
						throw new ComponentException(Authorization.ITEM_NOT_FOUND,
													 "Not found item with id = " + itemId);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (NumberFormatException ex) {
			throw new ComponentException(Authorization.ITEM_NOT_FOUND, "Not found item with id = " + itemId);
		} catch (SQLException ex) {
			throw new TigaseDBException("Can't find position for item with id " + itemId + " in archive for room " +
												query.getComponentJID(), ex);
		}
	}

	protected int setStatementParamsForMAM(PreparedStatement st, Query query, String nodeIds) throws SQLException {
		int i = 1;
		st.setString(i++, nodeIds);
		data_repo.setTimestamp(st, i++, query.getStart() == null ? null : new Timestamp(query.getStart().getTime()));
		data_repo.setTimestamp(st, i++, query.getEnd() == null ? null : new Timestamp(query.getEnd().getTime()));
		st.setString(i++, query.getWith() == null ? null : query.getWith().toString());
//		if (query.getStart() != null) {
//			st.setTimestamp(i++, new Timestamp(query.getStart().getTime()));
//		} else {
//			st.setObject(i++, null);
//		}
//		if (query.getEnd() != null) {
//			st.setTimestamp(i++, new Timestamp(query.getEnd().getTime()));
//		} else {
//			st.setObject(i++, null);
//		}
//		if (query.getWith() != null) {
//			st.setString(i++, query.getWith().toString());
//		} else {
//			st.setObject(i++, null);
//		}
		st.setInt(i++, query.getOrder().ordinal());

		return i;
	}

	protected String readNodeConfigFormData(final BareJID serviceJid, final long nodeId) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "reding node config: serviceJid: {0}, nodeId: {1}", new Object[]{serviceJid, nodeId});
		}
		HashCode hash = null;
		try {
			ResultSet rs = null;
			hash = takeDao();
			PreparedStatement get_node_configuration_sp = data_repo.getPreparedStatement(hash.hashCode(),
																						 GET_NODE_CONFIGURATION_QUERY);
			synchronized (get_node_configuration_sp) {
				try {
					get_node_configuration_sp.setLong(1, nodeId);
					rs = get_node_configuration_sp.executeQuery();
					if (rs.next()) {
						return rs.getString(1);
					}
					return null;
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
		data_repo.initPreparedStatement(REMOVE_SERVICE_QUERY, REMOVE_SERVICE_QUERY);
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
		data_repo.initPreparedStatement(DELETE_ALL_NODES_QUERY, DELETE_ALL_NODES_QUERY);
		data_repo.initPreparedStatement(SET_NODE_CONFIGURATION_QUERY, SET_NODE_CONFIGURATION_QUERY);
		data_repo.initPreparedStatement(SET_NODE_AFFILIATION_QUERY, SET_NODE_AFFILIATION_QUERY);
		data_repo.initPreparedStatement(GET_NODE_CONFIGURATION_QUERY, GET_NODE_CONFIGURATION_QUERY);
		data_repo.initPreparedStatement(GET_NODE_AFFILIATIONS_QUERY, GET_NODE_AFFILIATIONS_QUERY);
		data_repo.initPreparedStatement(GET_NODE_SUBSCRIPTIONS_QUERY, GET_NODE_SUBSCRIPTIONS_QUERY);
		data_repo.initPreparedStatement(SET_NODE_SUBSCRIPTION_QUERY, SET_NODE_SUBSCRIPTION_QUERY);
		data_repo.initPreparedStatement(DELETE_NODE_SUBSCRIPTIONS_QUERY, DELETE_NODE_SUBSCRIPTIONS_QUERY);
		data_repo.initPreparedStatement(GET_USER_AFFILIATIONS_QUERY, GET_USER_AFFILIATIONS_QUERY);
		data_repo.initPreparedStatement(GET_USER_SUBSCRIPTIONS_QUERY, GET_USER_SUBSCRIPTIONS_QUERY);

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
