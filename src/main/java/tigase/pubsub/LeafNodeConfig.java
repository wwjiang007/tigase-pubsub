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

public class LeafNodeConfig
		extends AbstractNodeConfig {

	public LeafNodeConfig(final String nodeName) {
		super(nodeName);
		this.setNodeType(NodeType.leaf);
	}

	public LeafNodeConfig(final String nodeName, AbstractNodeConfig config) {
		super(nodeName, config);
	}

	public Integer getMaxItems() {
		Integer x = form.getAsInteger("pubsub#max_items");
		return x;
	}

	public boolean isPersistItem() {
		Boolean x = form.getAsBoolean("pubsub#persist_items");
		return x == null ? false : x;
	}

	@Override
	protected AbstractNodeConfig getInstance(String nodeName) {
		return new LeafNodeConfig(nodeName);
	}

	@Override
	protected void init() {
		super.init();
	}

}
