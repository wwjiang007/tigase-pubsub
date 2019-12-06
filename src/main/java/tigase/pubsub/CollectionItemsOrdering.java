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
package tigase.pubsub;

import tigase.pubsub.repository.IItems;

import java.util.Comparator;

public enum CollectionItemsOrdering {

	byCreationDate(1,"Sort items by creation time", new Comparator<IItems.ItemMeta>() {

		@Override
		public int compare(IItems.ItemMeta o1, IItems.ItemMeta o2) {
			return o1.getCreationDate().compareTo(o2.getCreationDate()) * (-1);
		}
	}),

	byUpdateDate(2,"Sort items by last update time", new Comparator<IItems.ItemMeta>() {

		@Override
		public int compare(IItems.ItemMeta o1, IItems.ItemMeta o2) {
			return o1.getItemUpdateDate().compareTo(o2.getItemUpdateDate()) * (-1);
		}
	}),;

	private final Comparator<IItems.ItemMeta> comparator;
	private final String description;
	private final int value;

	public static String[] descriptions() {
		String[] result = new String[values().length];
		int i = 0;
		for (CollectionItemsOrdering item : values()) {
			result[i++] = item.description;
		}
		return result;
	}

	private CollectionItemsOrdering(int value, String description, Comparator<IItems.ItemMeta> cmp) {
		this.description = description;
		this.comparator = cmp;
		this.value = value;
	}

	public Comparator<IItems.ItemMeta> getComparator() {
		return comparator;
	}

	public int value() {
		return value;
	}

}
