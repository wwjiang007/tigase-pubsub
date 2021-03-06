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

	public abstract Element getItem(String id) throws RepositoryException;

	public abstract Date getItemCreationDate(String id) throws RepositoryException;

	@Deprecated
	default String[] getItemsIds() throws RepositoryException {
		return getItemsIds(CollectionItemsOrdering.byUpdateDate);
	}

	public abstract String[] getItemsIds(CollectionItemsOrdering order) throws RepositoryException;

	@Deprecated
	default String[] getItemsIdsSince(Date since) throws RepositoryException {
		return getItemsIdsSince(CollectionItemsOrdering.byUpdateDate, since);
	}

	public abstract String[] getItemsIdsSince(CollectionItemsOrdering order, Date since) throws RepositoryException;

	public abstract List<ItemMeta> getItemsMeta() throws RepositoryException;

	public abstract Date getItemUpdateDate(String id) throws RepositoryException;

	public abstract void writeItem(long timeInMilis, String id, String publisher, Element item)
			throws RepositoryException;

	public static class ItemMeta {

		private final Date creationDate;
		private final String id;
		private final String node;
		private final Date updateDate;

		public ItemMeta(String node, String id, Date creationDate) {
			this.node = node;
			this.id = id;
			this.creationDate = creationDate;
			this.updateDate = creationDate;
		}

		public ItemMeta(String node, String id, Date creationDate, Date updateDate) {
			this.node = node;
			this.id = id;
			this.creationDate = creationDate;
			this.updateDate = updateDate;
		}

		public Date getCreationDate() {
			return creationDate;
		}

		public String getId() {
			return id;
		}

		public Date getItemUpdateDate() {
			return updateDate;
		}

		public String getNode() {
			return node;
		}
	}

}
