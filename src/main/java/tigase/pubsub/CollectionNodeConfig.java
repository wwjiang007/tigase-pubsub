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
package tigase.pubsub;

import tigase.form.Field;

public class CollectionNodeConfig
		extends AbstractNodeConfig {

	public CollectionNodeConfig(String nodeName) {
		super(nodeName);
	}

//	public void addChildren(String... children) {
//		Set<String> list = new HashSet<String>();
//		String[] cur = getChildren();
//		if (cur != null)
//			list.addAll(Arrays.asList(cur));
//		for (String kid : children) {
//			list.add(kid);
//		}
//		setChildren(list.toArray(new String[] {}));
//	}

	public void setChildren(String[] children) {
		setValue("pubsub#children", children);
	}

	@Override
	protected AbstractNodeConfig getInstance(String nodeName) {
		return new CollectionNodeConfig(nodeName);
	}

//	public void removeChildren(String nodeName) {
//		Set<String> list = new HashSet<String>();
//		String[] cur = getChildren();
//		if (cur != null)
//			list.addAll(Arrays.asList(cur));
//		list.remove(nodeName);
//		setChildren(list.toArray(new String[] {}));
//	}

	@Override
	protected void init() {
		super.init();
		Field f = Field.fieldTextMulti("pubsub#children", "", null);
		add(f);
	}

}
