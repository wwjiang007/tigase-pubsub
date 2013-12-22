package tigase.pubsub.repository.cached;

import tigase.pubsub.AbstractNodeConfig;
import tigase.xmpp.BareJID;

public class Node {

	//private boolean affNeedsWriting = false;
	private boolean conNeedsWriting = false;
	private long creationTime = System.currentTimeMillis();

	private boolean deleted = false;
	private String name;
	private long nodeId;

	// private Long nodeAffiliationsChangeTimestamp;

	private NodeAffiliations nodeAffiliations;
	private AbstractNodeConfig nodeConfig;
	private NodeSubscriptions nodeSubscriptions;

	// private Long nodeConfigChangeTimestamp;

	private BareJID serviceJid;
	//private boolean subNeedsWriting = false;

	// private Long nodeSubscriptionsChangeTimestamp;

	public Node(long nodeId, BareJID serviceJid, AbstractNodeConfig nodeConfig, NodeAffiliations nodeAffiliations,
			NodeSubscriptions nodeSubscriptions) {
		this.nodeId = nodeId;
		this.serviceJid = serviceJid;
		this.nodeConfig = nodeConfig;
		this.nodeAffiliations = nodeAffiliations;
		this.nodeSubscriptions = nodeSubscriptions;
		this.name = nodeConfig.getNodeName();
	}
	
	protected long getNodeId() {
		return nodeId;
	}

	public void affiliationsMerge() {
		synchronized (this) {
			nodeAffiliations.merge();
			//affNeedsWriting = true;
		}
	}

	public boolean affiliationsNeedsWriting() {
		return nodeAffiliations.isChanged();
	}

	public void affiliationsSaved() {
	//	affNeedsWriting = false;
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

	public long getCreationTime() {
		return creationTime;
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

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	// public Long getNodeSubscriptionsChangeTimestamp() {
	// return nodeSubscriptionsChangeTimestamp;
	// }

	public void subscriptionsMerge() {
		synchronized (this) {
			nodeSubscriptions.merge();
			//subNeedsWriting = true;
		}
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

	public boolean subscriptionsNeedsWriting() {
		return nodeSubscriptions.isChanged();
	}

	public void subscriptionsSaved() {
		//subNeedsWriting = false;
		this.subscriptionsMerge();
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

}
