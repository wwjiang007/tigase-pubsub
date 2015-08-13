/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.repository.migration;

import java.util.Date;

import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public interface IPubSubOldDAO {

	public static class Item {
		public Date creationDate;
		public String id;
		public Element item;
		public String publisher;
		public Date updateDate;
	}

	Item getItem(BareJID serviceJid, String nodeName, String id) throws RepositoryException;

	String[] getItemsIds(BareJID serviceJid, String nodeName) throws RepositoryException;

	UsersAffiliation[] getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException;

	AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException;

	Date getNodeCreationDate(BareJID serviceJid, String nodeName) throws RepositoryException;

	BareJID getNodeCreator(BareJID serviceJid, String nodeName) throws RepositoryException;

	String[] getNodesList(BareJID serviceJids) throws RepositoryException;

	UsersSubscription[] getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException;

	BareJID[] getServiceJids() throws RepositoryException;

	void init() throws RepositoryException;
}
