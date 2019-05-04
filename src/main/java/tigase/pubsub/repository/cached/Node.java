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
package tigase.pubsub.repository.cached;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.repository.INodeMeta;
import tigase.xmpp.jid.BareJID;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Node<T>
		implements INodeMeta<T> {

	private static final Logger log = Logger.getLogger(Node.class.getName());
	private final Date creationTime;
	private final BareJID creator;
	private CopyOnWriteArrayList<String> childNodes;
	// private boolean affNeedsWriting = false;
	private boolean conNeedsWriting = false;
	private boolean deleted = false;
	private String name;
	private NodeAffiliations nodeAffiliations;

	// private Long nodeAffiliationsChangeTimestamp;

	private AbstractNodeConfig nodeConfig;
	private final T nodeId;
	private NodeSubscriptions nodeSubscriptions;

	// private Long nodeConfigChangeTimestamp;

	private BareJID serviceJid;
	// private boolean subNeedsWriting = false;

	// private Long nodeSubscriptionsChangeTimestamp;

	public Node(T nodeId, BareJID serviceJid, AbstractNodeConfig nodeConfig, NodeAffiliations nodeAffiliations,
				NodeSubscriptions nodeSubscriptions, BareJID creator, Date creationTime) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"Constructing Node, serviceJid: {0}, nodeConfig: {1}, nodeId: {2}, nodeAffiliations: {3}, nodeSubscriptions: {4}",
					new Object[]{serviceJid, nodeConfig, nodeId, nodeAffiliations, nodeSubscriptions});
		}

		this.nodeId = nodeId;
		this.serviceJid = serviceJid;
		this.nodeConfig = nodeConfig;
		this.nodeAffiliations = nodeAffiliations;
		this.nodeSubscriptions = nodeSubscriptions;
		this.name = nodeConfig.getNodeName();
		this.creator = creator;
		this.creationTime = creationTime;
	}

	public void affiliationsMerge() {
		nodeAffiliations.merge();
	}

	public boolean affiliationsNeedsWriting() {
		return nodeAffiliations.isChanged();
	}

	public void affiliationsSaved() {
		// affNeedsWriting = false;
		affiliationsMerge();
	}

	public void configCopyFrom(AbstractNodeConfig nodeConfig) {
		synchronized (this) {
			this.nodeConfig.copyFrom(nodeConfig);
			conNeedsWriting = true;
		}
	}

	public boolean configNeedsWriting() {
		return conNeedsWriting;
	}

	public void configSaved() {
		conNeedsWriting = false;
	}

	// public Long getNodeAffiliationsChangeTimestamp() {
	// return nodeAffiliationsChangeTimestamp;
	// }

	public String[] getChildNodes() {
		if (childNodes == null) {
			return null;
		}
		return childNodes.toArray(new String[0]);
	}

	protected void setChildNodes(List<String> childNodes) {
		synchronized (this) {
			if (this.childNodes == null) {
				this.childNodes = new CopyOnWriteArrayList<>(childNodes);
			} else {
				this.childNodes.addAllAbsent(childNodes);
				this.childNodes.retainAll(childNodes);
			}
		}
	}

	public Date getCreationTime() {
		return creationTime;
	}

	public BareJID getCreator() {
		return creator;
	}

	public String getName() {
		return name;
	}

	public NodeAffiliations getNodeAffiliations() {
		return nodeAffiliations;
	}

	public AbstractNodeConfig getNodeConfig() {
		return nodeConfig;
	}

	// public Long getNodeConfigChangeTimestamp() {
	// return nodeConfigChangeTimestamp;
	// }

	public T getNodeId() {
		return nodeId;
	}

	public NodeSubscriptions getNodeSubscriptions() {
		return nodeSubscriptions;
	}

	public BareJID getServiceJid() {
		return serviceJid;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	// public Long getNodeSubscriptionsChangeTimestamp() {
	// return nodeSubscriptionsChangeTimestamp;
	// }

	public boolean needsWriting() {
		return affiliationsNeedsWriting() || subscriptionsNeedsWriting() || conNeedsWriting;
	}

	// public void resetNodeAffiliationsChangeTimestamp() {
	// this.nodeAffiliationsChangeTimestamp = null;
	// }
	//
	// public void resetNodeConfigChangeTimestamp() {
	// this.nodeConfigChangeTimestamp = null;
	// }
	//
	// public void resetNodeSubscriptionsChangeTimestamp() {
	// this.nodeSubscriptionsChangeTimestamp = null;
	// }

	public void resetChanges() {
		nodeAffiliations.resetChangedFlag();
		nodeSubscriptions.resetChangedFlag();
	}

	public void subscriptionsMerge() {
		nodeSubscriptions.merge();
	}

	// public void setNodeAffiliationsChangeTimestamp() {
	// if (nodeAffiliationsChangeTimestamp == null)
	// nodeAffiliationsChangeTimestamp = System.currentTimeMillis();
	// }
	//
	// public void setNodeConfigChangeTimestamp() {
	// if (nodeConfigChangeTimestamp == null)
	// nodeConfigChangeTimestamp = System.currentTimeMillis();
	// }
	//
	// public void setNodeSubscriptionsChangeTimestamp() {
	// if (nodeSubscriptionsChangeTimestamp == null)
	// nodeSubscriptionsChangeTimestamp = System.currentTimeMillis();
	// }

	public boolean subscriptionsNeedsWriting() {
		return nodeSubscriptions.isChanged();
	}

	public void subscriptionsSaved() {
		// subNeedsWriting = false;
		this.subscriptionsMerge();
	}

	@Override
	public String toString() {
		return "Node{" + "creationTime=" + creationTime + ", deleted=" + deleted + ", name=" + name + ", nodeId=" +
				nodeId + ", nodeAffiliations=" + nodeAffiliations + ", nodeSubscriptions=" + nodeSubscriptions +
				", serviceJid=" + serviceJid + ", creator=" + creator + '}';
	}

	public void childNodeAdded(String childNode) {
		synchronized (this) {
			if (this.childNodes == null) {
				return;
			}
			this.childNodes.addIfAbsent(childNode);
		}
	}

	public void childNodeRemoved(String childNode) {
		synchronized (this) {
			if (this.childNodes == null) {
				return;
			}

			this.childNodes.remove(childNode);
		}
	}

}
