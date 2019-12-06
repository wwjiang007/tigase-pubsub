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
package tigase.pubsub.repository.converter;

import tigase.db.converter.RowEntity;
import tigase.pubsub.*;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.cached.NodeAffiliations;
import tigase.pubsub.repository.cached.NodeSubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.jid.BareJID;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * [host] [varchar] (255) NOT NULL,
 * [node] [varchar] (255) NOT NULL,
 * [parent] [varchar] (255) NOT NULL DEFAULT '',
 * [plugin] [text] NOT NULL,
 * [nodeid] [bigint] IDENTITY(1,1) NOT NULL,
 */
class PubSubNodeEntity
		implements RowEntity {

	private final String node;
	private final IAffiliations nodeAffiliations = new NodeAffiliations();
	private final AbstractNodeConfig nodeConfig;
	private final ISubscriptions nodeSubscriptions = new NodeSubscriptions();
	private final NodeType nodeType;
	private final long nodeid;
	private final String parent;
	private final BareJID service;
	private Set<BareJID> owner = new ConcurrentSkipListSet<>();

	PubSubNodeEntity(BareJID service, String node, String parent, NodeType nodeType, long nodeid) {
		this.service = service;
		this.node = node;
		this.parent = parent;
		this.nodeid = nodeid;
		this.nodeType = nodeType;
		if (NodeType.collection.equals(nodeType)) {
			nodeConfig = new CollectionNodeConfig(node);
		} else {
			nodeConfig = new LeafNodeConfig(node);
		}
		nodeConfig.setCollection(parent);
	}

	@Override
	public String getID() {
		return service.toString() + " / " + node;
	}

	public NodeType getNodeType() {
		return nodeType;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		PubSubNodeEntity that = (PubSubNodeEntity) o;

		if (nodeid != that.nodeid) {
			return false;
		}
		if (!node.equals(that.node)) {
			return false;
		}
		if (nodeType != that.nodeType) {
			return false;
		}
		if (parent != null ? !parent.equals(that.parent) : that.parent != null) {
			return false;
		}
		return service.equals(that.service);
	}

	@Override
	public int hashCode() {
		int result = node.hashCode();
		result = 31 * result + nodeType.hashCode();
		result = 31 * result + (int) (nodeid ^ (nodeid >>> 32));
		result = 31 * result + (parent != null ? parent.hashCode() : 0);
		result = 31 * result + service.hashCode();
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("PubSubNodeEntity{");
		sb.append("node='").append(node).append('\'');
		sb.append(", nodeid=").append(nodeid);
		sb.append(", nodeType=").append(nodeType);
		sb.append(", parent=").append(parent);
		sb.append(", service=").append(service);
		sb.append(", owner=").append(owner);
		sb.append(", nodeAffiliations=").append(nodeAffiliations);
		sb.append(", nodeSubscriptions=").append(nodeSubscriptions);
		sb.append(", nodeConfig=").append(nodeConfig);
		sb.append('}');
		return sb.toString();
	}

	String getNode() {
		return node;
	}

	UsersAffiliation[] getNodeAffiliations() {
		return nodeAffiliations.getAffiliations();
	}

	AbstractNodeConfig getNodeConfig() {
		return nodeConfig;
	}

	UsersSubscription[] getNodeSubscriptions() {
		return nodeSubscriptions.getSubscriptions();
	}

	BareJID getService() {
		return service;
	}

	long getNodeid() {
		return nodeid;
	}

	Set<BareJID> getOwner() {
		return Collections.unmodifiableSet(owner);
	}

	void addOwner(BareJID owner) {
		this.owner.add(owner);
	}

	void addOwners(List<BareJID> owners) {
		this.owner.addAll(owners);
	}

	String getParent() {
		return parent != null ? parent : "";
	}

	void setConfigValue(String var, Object data) {
		nodeConfig.setValue(AbstractNodeConfig.PUBSUB + var, data);
	}

	void setConfigValues(String option, String[] data) {
		nodeConfig.setValues(AbstractNodeConfig.PUBSUB + option, data);
	}

	String addSubscription(BareJID jid, Subscription subscription) {
		return nodeSubscriptions.addSubscriberJid(jid, subscription);
	}

	void addAffiliation(BareJID bareJid, Affiliation affiliation) {
		nodeAffiliations.addAffiliation(bareJid, affiliation);
	}
}
