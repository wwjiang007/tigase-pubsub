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
package tigase.pubsub.repository.derby;

import tigase.util.Algorithms;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.logging.Logger;

/**
 * @author andrzej
 */
public class StoredProcedures {

	private static final Logger log = Logger.getLogger(tigase.db.derby.StoredProcedures.class.getName());

	private static final Charset UTF8 = Charset.forName("UTF-8");

	protected static Long getIdOfJid(Connection conn, String jid) throws SQLException {
		if (jid == null) {
			return null;
		}

		String jidSha = sha1OfLower(jid);

		Statement st = conn.createStatement();
		try (ResultSet rs = st.executeQuery("select jid_id from tig_pubsub_jids where jid_sha1 = '" + jidSha + "'")) {
			if (rs.next()) {
				return rs.getLong(1);
			}
		}
		return null;
	}

	protected static String sha1OfLower(String data) throws SQLException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] hash = md.digest(data.toLowerCase().getBytes(UTF8));
			return Algorithms.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new SQLException(e);
		}
	}

	public static void tigPubSubCreateNode(String serviceJid, String nodeName, Integer nodeType, String nodeCreator,
										   String nodeConf, Long collectionId, Timestamp ts, String componentName, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			long serviceJidId = tigPubSubEnsureServiceJid(serviceJid, componentName);
			long nodeCreatorId = tigPubSubEnsureJid(nodeCreator);
			PreparedStatement ps = conn.prepareStatement(
					"insert into tig_pubsub_nodes (service_id,name,type,creator_id,creation_date,configuration,collection_id)" +
							" values (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			ps.setLong(1, serviceJidId);
			ps.setString(2, nodeName);
			ps.setInt(3, nodeType);
			ps.setLong(4, nodeCreatorId);
			ps.setTimestamp(5, ts);
			ps.setString(6, nodeConf);
			if (collectionId == null) {
				ps.setNull(7, java.sql.Types.BIGINT);
			} else {
				ps.setLong(7, collectionId);
			}

			ps.executeUpdate();
			data[0] = ps.getGeneratedKeys();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubDeleteAllNodes(String serviceJid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			String serviceJidSha1 = sha1OfLower(serviceJid);
			PreparedStatement ps = conn.prepareStatement(
					"delete from tig_pubsub_items where node_id in (" + "select n.node_id from tig_pubsub_nodes n" +
							" inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
							" where sj.service_jid_sha1 = ?)");
			ps.setString(1, serviceJidSha1);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_affiliations where node_id in (" +
											   "select n.node_id from tig_pubsub_nodes n" +
											   " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
											   " where sj.service_jid_sha1 = ?)");
			ps.setString(1, serviceJidSha1);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id in (" +
											   "select n.node_id from tig_pubsub_nodes n" +
											   " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
											   " where sj.service_jid_sha1 = ?)");
			ps.setString(1, serviceJidSha1);
			ps.executeUpdate();
			ps = conn.prepareStatement(
					"delete from tig_pubsub_nodes where node_id in (" + "select n.node_id from tig_pubsub_nodes n" +
							" inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
							" where sj.service_jid_sha1 = ?)");
			ps.setString(1, serviceJidSha1);
			ps.executeUpdate();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubDeleteItem(Long nodeId, String itemId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_items where node_id = ? and id = ?");
			ps.setLong(1, nodeId);
			ps.setString(2, itemId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubDeleteNodeSubscription(Long nodeId, String jid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id = ?" +
																 " and jid_id = (select jid_id from tig_pubsub_jids where jid_sha1 = ?)");
			ps.setLong(1, nodeId);
			ps.setString(2, sha1OfLower(jid));
			ps.executeUpdate();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static Long tigPubSubEnsureJid(String jid) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		try {
			return tigPubSubEnsureJid(conn, jid);
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static Long tigPubSubEnsureJid(Connection conn, String jid) throws SQLException {
		String jidSha1 = sha1OfLower(jid);
		PreparedStatement ps = conn.prepareStatement("select jid_id from tig_pubsub_jids where jid_sha1 = ?");

		ps.setString(1, jidSha1);
		ResultSet rs = ps.executeQuery();
		if (rs.next()) {
			return rs.getLong(1);
		} else {
			ps = conn.prepareStatement("insert into tig_pubsub_jids (jid, jid_sha1) values (?, ?)",
					Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, jid);
			ps.setString(2, jidSha1);
			ps.executeUpdate();
			rs = ps.getGeneratedKeys();
			if (rs.next()) {
				return rs.getLong(1);
			}
		}
		return null;
	}

	public static Long tigPubSubEnsureServiceJid(String serviceJid, String componentName) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			String serviceJidSha1 = sha1OfLower(serviceJid);
			PreparedStatement ps = conn.prepareStatement(
					"select service_id from tig_pubsub_service_jids where service_jid_sha1 = ?");

			ps.setString(1, serviceJidSha1);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getLong(1);
			} else {
				ps = conn.prepareStatement(
						"insert into tig_pubsub_service_jids (service_jid, service_jid_sha1, component_name) values (?, ?, ?)",
						Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, serviceJid);
				ps.setString(2, serviceJidSha1);
				ps.setString(3, componentName);
				ps.executeUpdate();
				rs = ps.getGeneratedKeys();
				if (rs.next()) {
					return rs.getLong(1);
				}
			}
			return null;
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubFixItem(Long nodeId, String itemId, java.sql.Timestamp creationDate,
										java.sql.Timestamp updateDate) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_pubsub_items set creation_date = ?," + " update_date = ? where node_id = ? and id = ?");
			if (creationDate == null) {
				ps.setNull(1, java.sql.Types.TIMESTAMP);
			} else {
				ps.setTimestamp(1, creationDate);
			}
			if (updateDate == null) {
				ps.setNull(2, java.sql.Types.TIMESTAMP);
			} else {
				ps.setTimestamp(2, updateDate);
			}
			ps.setLong(3, nodeId);
			ps.setString(4, itemId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubFixNode(Long nodeId, java.sql.Timestamp creationDate) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_pubsub_nodes set creation_date = ?" + " where node_id = ?");
			if (creationDate == null) {
				ps.setNull(1, java.sql.Types.TIMESTAMP);
			} else {
				ps.setTimestamp(1, creationDate);
			}
			ps.setLong(2, nodeId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetAllNodes(String serviceJid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, n.node_id from tig_pubsub_nodes n" +
																 " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
																 " where sj.service_jid_sha1 = ?");
			ps.setString(1, sha1OfLower(serviceJid));
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetChildNodes(String serviceJid, String collection, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, n.node_id from tig_pubsub_nodes n" +
																 " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
																 " inner join tig_pubsub_nodes p on p.node_id = n.collection_id and p.service_id = sj.service_id" +
																 " where sj.service_jid_sha1 = ? and p.name = ?");
			ps.setString(1, sha1OfLower(serviceJid));
			ps.setString(2, collection);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetItem(Long nodeId, String itemId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select data, p.jid, creation_date, update_date " + "from tig_pubsub_items pi " +
							"inner join tig_pubsub_jids p on p.jid_id = pi.publisher_id " +
							"where node_id = ? and id = ?");
			ps.setLong(1, nodeId);
			ps.setString(2, itemId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeAffiliations(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select pj.jid, pa.affiliation" + " from tig_pubsub_affiliations pa" +
							" inner join tig_pubsub_jids pj on pa.jid_id = pj.jid_id" + " where pa.node_id = ?");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeConfiguration(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select configuration from tig_pubsub_nodes where node_id = ?");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeId(String serviceJid, String nodeName, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.node_id from tig_pubsub_nodes n " +
																 "inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id " +
																 "where sj.service_jid_sha1 = ? and n.name = ?");
			ps.setString(1, sha1OfLower(serviceJid));
			ps.setString(2, nodeName);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeItemIds(Long nodeId, Integer order, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(order == 1
					? "select id from tig_pubsub_items where node_id = ?" + " order by creation_date"
					: "select id from tig_pubsub_items where node_id = ?" + " order by update_date");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeItemIdsSince(Long nodeId, Integer order, java.sql.Timestamp since, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(order == 1
					? "select id from tig_pubsub_items where node_id = ?" +
																 " and creation_date >= ? order by creation_date"
					: "select id from tig_pubsub_items where node_id = ?" +
																 " and update_date >= ? order by update_date");
			ps.setLong(1, nodeId);
			ps.setTimestamp(2, since);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeItemsMeta(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select id, creation_date, update_date from tig_pubsub_items" +
																 " where node_id = ? order by creation_date");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeMeta(String serviceJid, String nodeName, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select n.node_id, n.configuration, cj.jid, n.creation_date " + "from tig_pubsub_nodes n " +
							"inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id " +
							"inner join tig_pubsub_jids cj on cj.jid_id = n.creator_id " +
							"where sj.service_jid_sha1 = ? and n.name = ?");
			ps.setString(1, sha1OfLower(serviceJid));
			ps.setString(2, nodeName);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetNodeSubscriptions(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select pj.jid, ps.subscription, ps.subscription_id" + " from tig_pubsub_subscriptions ps" +
							" inner join tig_pubsub_jids pj on ps.jid_id = pj.jid_id" + " where ps.node_id = ?");
			ps.setLong(1, nodeId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetRootNodes(String serviceJid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, n.node_id from tig_pubsub_nodes n" +
																 " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
																 " where sj.service_jid_sha1 = ? and collection_id is null");
			ps.setString(1, sha1OfLower(serviceJid));
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetUserAffiliations(String serviceJid, String jid, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select n.name, pa.affiliation" + " from tig_pubsub_nodes n" +
																 " inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id" +
																 " inner join tig_pubsub_affiliations pa on pa.node_id = n.node_id" +
																 " inner join tig_pubsub_jids pj on pj.jid_id = pa.jid_id" +
																 " where pj.jid_sha1 = ? and sj.service_jid_sha1 = ?");
			ps.setString(1, sha1OfLower(jid));
			ps.setString(2, sha1OfLower(serviceJid));
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubGetUserSubscriptions(String serviceJid, String jid, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select n.name, ps.subscription, ps.subscription_id" + " from tig_pubsub_nodes n" +
							" inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id" +
							" inner join tig_pubsub_subscriptions ps on ps.node_id = n.node_id" +
							" inner join tig_pubsub_jids pj on pj.jid_id = ps.jid_id" +
							" where pj.jid_sha1 = ? and sj.service_jid_sha1 = ?");
			ps.setString(1, sha1OfLower(jid));
			ps.setString(2, sha1OfLower(serviceJid));
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubMamQueryItemPosition(String nodesIds, Timestamp since, Timestamp to, String publisher,
													 Integer order, Long nodeId, String itemId, ResultSet[] data)
			throws SQLException {
		String ts = order == 1 ? "update_date" : "creation_date";
		String query = "select pi.node_id, pi.id, row_number() over () as position" + " from tig_pubsub_items pi" +
				" where pi.node_id in (" + nodesIds + ")" + " and (? is null or pi." + ts + " >= ?)" +
				" and (? is null or pi." + ts + " <= ?)" + " and (? is null or pi.publisher_id = ?)" + " order by pi." +
				ts;

		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			Long publisherId = getIdOfJid(conn, publisher);

			PreparedStatement st = conn.prepareStatement(query);
			st.setTimestamp(1, since);
			st.setTimestamp(2, since);
			st.setTimestamp(3, to);
			st.setTimestamp(4, to);
			if (publisherId != null) {
				st.setLong(5, publisherId);
				st.setLong(6, publisherId);
			} else {
				st.setNull(5, Types.BIGINT);
				st.setNull(6, Types.BIGINT);
			}

			int i = 0;
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					if (rs.getLong(1) == nodeId && itemId.equals(rs.getString(2))) {
						i = rs.getInt(3);
					}
				}
			}

			String q = "select " + i + " as position from SYSIBM.SYSDUMMY1 where " + i + " <> 0";
			data[0] = conn.prepareStatement(q).executeQuery();
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubMamQueryItems(String nodesIds, Timestamp since, Timestamp to, String publisher,
											  Integer order, Integer limit, Integer offset, ResultSet[] data)
			throws SQLException {
		String ts = order == 1 ? "update_date" : "creation_date";
		String query = "select pn.name, pi.node_id, pi.id, pi." + ts + ", pi.data" + " from tig_pubsub_items pi" +
				" inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id" + " where pi.node_id in (" + nodesIds +
				")" + " and (? is null or pi." + ts + " >= ?)" + " and (? is null or pi." + ts + " <= ?)" +
				" and (? is null or pi.publisher_id = ?)" + " order by pi." + ts +
				" offset ? rows fetch next ? rows only";

		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			Long publisherId = getIdOfJid(conn, publisher);

			PreparedStatement st = conn.prepareStatement(query);
			st.setTimestamp(1, since);
			st.setTimestamp(2, since);
			st.setTimestamp(3, to);
			st.setTimestamp(4, to);
			if (publisherId != null) {
				st.setLong(5, publisherId);
				st.setLong(6, publisherId);
			} else {
				st.setNull(5, Types.BIGINT);
				st.setNull(6, Types.BIGINT);
			}
			st.setInt(7, offset);
			st.setInt(8, limit);

			data[0] = st.executeQuery();
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubMamQueryItemsCount(String nodesIds, Timestamp since, Timestamp to, String publisher,
												   Integer order, ResultSet[] data) throws SQLException {
		String ts = order == 1 ? "update_date" : "creation_date";
		String query = "select count(1)" + " from tig_pubsub_items pi" + " where pi.node_id in (" + nodesIds + ")" +
				" and (? is null or pi." + ts + " >= ?)" + " and (? is null or pi." + ts + " <= ?)" +
				" and (? is null or pi.publisher_id = ?)";

		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			Long publisherId = getIdOfJid(conn, publisher);

			PreparedStatement st = conn.prepareStatement(query);
			st.setTimestamp(1, since);
			st.setTimestamp(2, since);
			st.setTimestamp(3, to);
			st.setTimestamp(4, to);
			if (publisherId != null) {
				st.setLong(5, publisherId);
				st.setLong(6, publisherId);
			} else {
				st.setNull(5, Types.BIGINT);
				st.setNull(6, Types.BIGINT);
			}

			data[0] = st.executeQuery();
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubRemoveNode(Long nodeId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_pubsub_items where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_affiliations where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_nodes where node_id = ?");
			ps.setLong(1, nodeId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubRemoveService(String serviceJid, String componentName, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			String serviceJidSha1 = sha1OfLower(serviceJid);
			PreparedStatement ps = conn.prepareStatement(
					"delete from tig_pubsub_items where node_id in (" + "select n.node_id from tig_pubsub_nodes n" +
							" inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
							" where sj.service_jid_sha1 = ? and sj.component_name = ?)");
			ps.setString(1, serviceJidSha1);
			ps.setString(2, componentName);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_affiliations where node_id in (" +
											   "select n.node_id from tig_pubsub_nodes n" +
											   " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
											   " where sj.service_jid_sha1 = ? and sj.component_name = ?)");
			ps.setString(1, serviceJidSha1);
			ps.setString(2, componentName);
			ps.executeUpdate();
			ps = conn.prepareStatement("delete from tig_pubsub_subscriptions where node_id in (" +
											   "select n.node_id from tig_pubsub_nodes n" +
											   " inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
											   " where sj.service_jid_sha1 = ? and sj.component_name = ?)");
			ps.setString(1, serviceJidSha1);
			ps.setString(2, componentName);
			ps.executeUpdate();
			ps = conn.prepareStatement(
					"delete from tig_pubsub_nodes where node_id in (" + "select n.node_id from tig_pubsub_nodes n" +
							" inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id" +
							" where sj.service_jid_sha1 = ? and sj.component_name = ?)");
			ps.setString(1, serviceJidSha1);
			ps.setString(2, componentName);
			ps.executeUpdate();

			ps = conn.prepareStatement("delete from tig_pubsub_service_jids where service_jid_sha1 = ? and component_name = ?");
			ps.setString(1, serviceJidSha1);
			ps.setString(2, componentName);
			ps.executeUpdate();
			ps = conn.prepareStatement(
					"delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = ?)");
			ps.setString(1, serviceJidSha1);
			ps.executeUpdate();
			ps = conn.prepareStatement(
					"delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = ?)");
			ps.setString(1, serviceJidSha1);
			ps.executeUpdate();

		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubSetNodeAffiliation(Long nodeId, String jid, String affil, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select 1 from tig_pubsub_affiliations pa" +
																 " inner join tig_pubsub_jids pj on pa.jid_id = pj.jid_id" +
																 " where pa.node_id = ? and pj.jid_sha1 = ?");
			ps.setLong(1, nodeId);
			ps.setString(2, sha1OfLower(jid));
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				if ("none".equals(affil)) {
					ps = conn.prepareStatement(
							"delete from tig_pubsub_affiliations" + " where node_id = ? and jid_id = (" +
									" select jid_id from tig_pubsub_jids where jid_sha1 = ?)");
					ps.setLong(1, nodeId);
					ps.setString(2, sha1OfLower(jid));
					ps.executeUpdate();
				} else {
					long jidId = tigPubSubEnsureJid(jid);
					ps = conn.prepareStatement(
							"update tig_pubsub_affiliations set affiliation = ?" + " where node_id = ? and jid_id = ?");
					ps.setString(1, affil);
					ps.setLong(2, nodeId);
					ps.setLong(3, jidId);
					ps.executeUpdate();
				}
			} else {
				if ("none".equals(affil)) {
					return;
				}

				long jidId = tigPubSubEnsureJid(jid);
				ps = conn.prepareStatement(
						"insert into tig_pubsub_affiliations (node_id, jid_id," + " affiliation) values (?, ?, ?)");
				ps.setLong(1, nodeId);
				ps.setLong(2, jidId);
				ps.setString(3, affil);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubSetNodeConfiguration(Long nodeId, String conf, Long collectionId, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_pubsub_nodes " + "set configuration = ?, collection_id = ? where node_id = ?");
			ps.setString(1, conf);
			if (collectionId == null) {
				ps.setNull(2, java.sql.Types.BIGINT);
			} else {
				ps.setLong(2, collectionId);
			}
			ps.setLong(3, nodeId);
			ps.executeUpdate();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubSetNodeSubscription(Long nodeId, String jid, String subscr, String subscrId,
													ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			long jidId = tigPubSubEnsureJid(jid);
			PreparedStatement ps = conn.prepareStatement(
					"select 1 from tig_pubsub_subscriptions" + " where node_id = ? and jid_id = ?");
			ps.setLong(1, nodeId);
			ps.setLong(2, jidId);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				ps = conn.prepareStatement(
						"update tig_pubsub_subscriptions set subscription = ?" + " where node_id = ? and jid_id = ?");
				ps.setString(1, subscr);
				ps.setLong(2, nodeId);
				ps.setLong(3, jidId);
				ps.executeUpdate();
			} else {
				ps = conn.prepareStatement("insert into tig_pubsub_subscriptions (node_id, jid_id," +
												   " subscription, subscription_id) values (?, ?, ?, ?)");
				ps.setLong(1, nodeId);
				ps.setLong(2, jidId);
				ps.setString(3, subscr);
				ps.setString(4, subscrId);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubWriteItem(Long nodeId, String itemId, String publisher, String itemData, Timestamp ts,
										  ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			synchronized (StoredProcedures.class) {
				PreparedStatement ps = conn.prepareStatement(
						"update tig_pubsub_items set update_date = ?, data = ? " + "where node_id = ? and id = ?");
				ps.setTimestamp(1, ts);
				ps.setString(2, itemData);
				ps.setLong(3, nodeId);
				ps.setString(4, itemId);
				int updated = ps.executeUpdate();
				if (updated == 0) {
					long publisherId = tigPubSubEnsureJid(conn, publisher);
					ps = conn.prepareStatement("insert into tig_pubsub_items (node_id, id, creation_date, " +
													   "update_date, publisher_id, data) values (?, ?, ?, ?, ?, ?)");
					ps.setLong(1, nodeId);
					ps.setString(2, itemId);
					ps.setTimestamp(3, ts);
					ps.setTimestamp(4, ts);
					ps.setLong(5, publisherId);
					ps.setString(6, itemData);
					ps.executeUpdate();
				}
			}
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigPubSubCountNodes(String serviceJid, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = null;
			if (serviceJid == null) {
				ps = conn.prepareStatement("select count(1) from tig_pubsub_nodes");
			} else {
				String serviceJidSha1 = sha1OfLower(serviceJid);
				ps = conn.prepareStatement("select count(1) from tig_pubsub_nodes n inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id where sj.service_jid_sha1 = ?)");
				ps.setString(1, serviceJidSha1);
			}
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}
}
