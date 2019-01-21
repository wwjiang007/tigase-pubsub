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

import java.util.Date;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.repository.RepositoryException;
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
		public String id;
		public String publisher;
		public Date creationDate;
		public Date updateDate;
		public Element item;
	}
	
	BareJID[] getServiceJids() throws RepositoryException;
	
	String[] getNodesList(BareJID serviceJids) throws RepositoryException;
	
	Date getNodeCreationDate(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	BareJID getNodeCreator(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	UsersAffiliation[] getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException;
	UsersSubscription[] getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	String[] getItemsIds(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	Item getItem(BareJID serviceJid, String nodeName, String id) throws RepositoryException;
	
	void init() throws RepositoryException;
}
