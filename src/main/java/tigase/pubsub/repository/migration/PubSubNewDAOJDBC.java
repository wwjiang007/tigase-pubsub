/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.repository.migration;

import tigase.component.exceptions.RepositoryException;
import tigase.xmpp.BareJID;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.Date;

/**
 *
 * @author andrzej
 */
public class PubSubNewDAOJDBC extends tigase.pubsub.repository.PubSubDAOJDBC {

	private CallableStatement fix_item_st = null;
	private CallableStatement fix_node_st = null;

	public PubSubNewDAOJDBC() {
	}

	public void fixItem(BareJID serviceJid, long nodeId, String itemId, Date creationDate, Date updateDate)
			throws RepositoryException {
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
		} catch (SQLException ex) {
			throw new RepositoryException("could not fix node creation date", ex);
		}
	}

	public void fixNode(BareJID serviceJid, long nodeId, Date creationDate) throws RepositoryException {
		if (creationDate == null)
			return;
		try {
			synchronized (fix_node_st) {
				fix_node_st.setLong(1, nodeId);
				fix_node_st.setTimestamp(2, new java.sql.Timestamp(creationDate.getTime()));
				fix_node_st.execute();
			}
		} catch (SQLException ex) {
			throw new RepositoryException("could not fix node creation date", ex);
		}
	}

}
