/**
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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.repository.migration;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import tigase.db.UserRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public class PubSubNewDAOJDBC extends tigase.pubsub.repository.PubSubDAOJDBC {

	private CallableStatement fix_node_st = null;
	private CallableStatement fix_item_st = null;
	
	public PubSubNewDAOJDBC() {
	}
	
	@Override
	public void init(String resource_uri, Map<String, String> params, UserRepository userRepository) throws RepositoryException {
		super.init(resource_uri, params, userRepository);
		try {
			fix_node_st = conn.prepareCall("{ call TigPubSubFixNode(?,?) }");
			fix_item_st = conn.prepareCall("{ call TigPubSubFixItem(?,?,?,?) }");
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not initialize repository", ex);
		}
	}
	
	public void fixNode(BareJID serviceJid, long nodeId, Date creationDate) 
			throws RepositoryException {
		if (creationDate == null)
			return;
		try {
			synchronized (fix_node_st) {
				fix_node_st.setLong(1, nodeId);
				fix_node_st.setTimestamp(2, new java.sql.Timestamp(creationDate.getTime()));
				fix_node_st.execute();
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not fix node creation date", ex);
		}
	}
	
	public void fixItem(BareJID serviceJid, long nodeId, String itemId, Date creationDate, Date updateDate) throws RepositoryException {
		try {
			synchronized (fix_item_st) {
				fix_item_st.setLong(1, nodeId);
				fix_item_st.setString(2, itemId);
				if (creationDate == null) {
					fix_item_st.setNull(3, java.sql.Types.TIMESTAMP);
				} else {
					fix_item_st.setTimestamp(3, new java.sql.Timestamp(creationDate.getTime()));
				}
				if (updateDate == null) {
					fix_item_st.setNull(4, java.sql.Types.TIMESTAMP);
				} else {
					fix_item_st.setTimestamp(4, new java.sql.Timestamp(updateDate.getTime()));
				}
				fix_item_st.execute();
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not fix node creation date", ex);
		} 
	}
}
