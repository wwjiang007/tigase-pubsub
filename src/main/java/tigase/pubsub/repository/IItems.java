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

import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.CollectionItemsOrdering;
import tigase.xml.Element;

import java.util.Date;
import java.util.List;

public interface IItems {

	public abstract void deleteItem(String id) throws RepositoryException;

	public abstract IItem getItem(String id) throws RepositoryException;
	
	public abstract String[] getItemsIds(CollectionItemsOrdering order) throws RepositoryException;
	
	public abstract String[] getItemsIdsSince(CollectionItemsOrdering order, Date since) throws RepositoryException;

	public abstract List<ItemMeta> getItemsMeta() throws RepositoryException;

	public abstract void writeItem(long timeInMilis, String id, String publisher, Element item, String uuid)
			throws RepositoryException;

	interface IItemBase {

		String getId();

		String getNode();

		String getUUID();

	}

	interface IItem extends IItemBase {

		Element getItem();

	}

	public static class ItemBase {

		private final String id;
		private final String node;
		private final String uuid;

		protected ItemBase(String node, String id, String uuid) {
			this.node = node;
			this.id = id;
			if (uuid != null) {
				this.uuid = uuid.toLowerCase();
			} else {
				this.uuid = null;
			}
		}

		public String getId() {
			return id;
		}

		public String getUUID() { return uuid; }

		public String getNode() {
			return node;
		}

	}

	public static class Item extends ItemBase implements IItem {

		private final Element item;

		public Item(String node, String id, String uuid, Element item) {
			super(node, id, uuid);
			this.item = item;
		}

		@Override
		public Element getItem() {
			return item;
		}

	}

	public static class ItemMeta extends ItemBase {

		private final Date creationDate;
		private final Date updateDate;

		public ItemMeta(String node, String id, Date creationDate, Date updateDate, String uuid) {
			super(node, id, uuid);
			this.creationDate = creationDate;
			this.updateDate = updateDate;
		}

		public Date getCreationDate() {
			return creationDate;
		}

		public Date getItemUpdateDate() {
			return updateDate;
		}

	}

}
