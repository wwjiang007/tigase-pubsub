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
package tigase.pubsub.repository.cached;

import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.CollectionItemsOrdering;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;

import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class Items<T>
		implements IItems {

	private static final Logger log = Logger.getLogger(Items.class.getName());

	private final IPubSubDAO<T, ?, ?> dao;

	private final T nodeId;

	private final String nodeName;

	private final BareJID serviceJid;

	public Items(T nodeId, BareJID serviceJid, String nodeName, IPubSubDAO dao) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Constructing Items, serviceJid: {0}, nodeName: {1}, nodeId: {2}, dao: {3}",
					new Object[]{serviceJid, nodeName, nodeId, dao});
		}
		this.nodeId = nodeId;
		this.dao = dao;
		this.nodeName = nodeName;
		this.serviceJid = serviceJid;
	}

	@Override
	public void deleteItem(String id) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Deleting item, serviceJid: {0}, id: {1}, nodeId: {2}, dao: {3}",
					new Object[]{serviceJid, id, nodeId, dao});
		}
		this.dao.deleteItem(serviceJid, nodeId, id);
	}

	@Override
	public IItem getItem(String id) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getItem, serviceJid: {0}, id: {1}, nodeId: {2}, dao: {3}",
					new Object[]{serviceJid, id, nodeId, dao});
		}
		return this.dao.getItem(serviceJid, nodeId, id);
	}
	
	@Override
	public String[] getItemsIds(CollectionItemsOrdering order) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getItemsIds, serviceJid: {0}, nodeId: {1}, dao: {2}, order: {3}",
					new Object[]{serviceJid, nodeId, dao, order});
		}
		return this.dao.getItemsIds(serviceJid, nodeId, order);
	}

	@Override
	public String[] getItemsIdsSince(CollectionItemsOrdering order, Date since) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getItemsIdsSince, serviceJid: {0}, nodeId: {1}, dao: {2}, order: {3}, since: {4}",
					new Object[]{serviceJid, nodeId, dao, order, since});
		}
		return this.dao.getItemsIdsSince(serviceJid, nodeId, order, since);
	}

	@Override
	public List<IItems.ItemMeta> getItemsMeta() throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getItemsIdsSince, serviceJid: {0}, nodeId: {1}, dao: {2}",
					new Object[]{serviceJid, nodeId, dao});
		}
		return this.dao.getItemsMeta(serviceJid, nodeId, nodeName);
	}
	
	@Override
	public void writeItem(long timeInMilis, String id, String publisher, Element item, String uuid) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"writeItem, serviceJid: {0}, nodeId: {1}, dao: {2}, id: {3}, publisher: {4}, item: {5}",
					new Object[]{serviceJid, nodeId, dao, id, publisher, item});
		}
		this.dao.writeItem(serviceJid, nodeId, timeInMilis, id, publisher, item, uuid);
	}

}
