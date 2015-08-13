package tigase.pubsub.repository.cached;

import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.pubsub.AbstractNodeConfig;
import tigase.xmpp.BareJID;

public class Node<T> {

	private static final Logger log = Logger.getLogger(Node.class.getName());

	// private boolean affNeedsWriting = false;
	private boolean conNeedsWriting = false;
	private long creationTime = System.currentTimeMillis();

	private boolean deleted = false;
	private String name;
	private NodeAffiliations nodeAffiliations;

	// private Long nodeAffiliationsChangeTimestamp;

	private AbstractNodeConfig nodeConfig;
	private T nodeId;
	private NodeSubscriptions nodeSubscriptions;

	// private Long nodeConfigChangeTimestamp;

	private BareJID serviceJid;
	// private boolean subNeedsWriting = false;

	// private Long nodeSubscriptionsChangeTimestamp;

	public Node(T nodeId, BareJID serviceJid, AbstractNodeConfig nodeConfig, NodeAffiliations nodeAffiliations,
			NodeSubscriptions nodeSubscriptions) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST,
					"Constructing Node, serviceJid: {0}, nodeConfig: {1}, nodeId: {2}, nodeAffiliations: {3}, nodeSubscriptions: {4}",
					new Object[] { serviceJid, nodeConfig, nodeId, nodeAffiliations, nodeSubscriptions });
		}

		this.nodeId = nodeId;
		this.serviceJid = serviceJid;
		this.nodeConfig = nodeConfig;
		this.nodeAffiliations = nodeAffiliations;
		this.nodeSubscriptions = nodeSubscriptions;
		this.name = nodeConfig.getNodeName();
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

	public long getCreationTime() {
		return creationTime;
	}

	// public Long getNodeAffiliationsChangeTimestamp() {
	// return nodeAffiliationsChangeTimestamp;
	// }

	public String getName() {
		return name;
	}

	public NodeAffiliations getNodeAffiliations() {
		return nodeAffiliations;
	}

	public AbstractNodeConfig getNodeConfig() {
		return nodeConfig;
	}

	public T getNodeId() {
		return nodeId;
	}

	// public Long getNodeConfigChangeTimestamp() {
	// return nodeConfigChangeTimestamp;
	// }

	public NodeSubscriptions getNodeSubscriptions() {
		return nodeSubscriptions;
	}

	public BareJID getServiceJid() {
		return serviceJid;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean needsWriting() {
		return affiliationsNeedsWriting() || subscriptionsNeedsWriting() || conNeedsWriting;
	}

	public void resetChanges() {
		nodeAffiliations.resetChangedFlag();
		nodeSubscriptions.resetChangedFlag();
	}

	// public Long getNodeSubscriptionsChangeTimestamp() {
	// return nodeSubscriptionsChangeTimestamp;
	// }

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
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

	public void subscriptionsMerge() {
		nodeSubscriptions.merge();
	}

	public boolean subscriptionsNeedsWriting() {
		return nodeSubscriptions.isChanged();
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

	public void subscriptionsSaved() {
		// subNeedsWriting = false;
		this.subscriptionsMerge();
	}

	@Override
	public String toString() {
		return "Node{" + "creationTime=" + creationTime + ", deleted=" + deleted + ", name=" + name + ", nodeId=" + nodeId
				+ ", nodeAffiliations=" + nodeAffiliations + ", nodeSubscriptions=" + nodeSubscriptions + ", serviceJid="
				+ serviceJid + '}';
	}
}
