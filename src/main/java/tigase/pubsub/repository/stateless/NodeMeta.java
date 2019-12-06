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
package tigase.pubsub.repository.stateless;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.repository.INodeMeta;
import tigase.xmpp.jid.BareJID;

import java.util.Date;

/**
 * Class implements INodeMeta interfaces and holds PubSub node metadata
 */
public class NodeMeta<T>
		implements INodeMeta<T> {

	private final AbstractNodeConfig config;
	private final Date creationTime;
	private final BareJID creator;
	private final T id;

	public NodeMeta(T id, AbstractNodeConfig config, BareJID creator, Date creationTime) {
		this.id = id;
		this.creationTime = creationTime;
		this.creator = creator;
		this.config = config;
	}

	public AbstractNodeConfig getNodeConfig() {
		return config;
	}

	@Override
	public T getNodeId() {
		return id;
	}

	@Override
	public Date getCreationTime() {
		return creationTime;
	}

	@Override
	public BareJID getCreator() {
		return creator;
	}
}
