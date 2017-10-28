/*
 * ClusterNodeMap.java
 *
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

package tigase.pubsub.cluster;

import tigase.xmpp.jid.JID;

import java.security.SecureRandom;
import java.util.*;
import java.util.Map.Entry;

public class ClusterNodeMap {

	private class NodeInfo {
		private String clusterNodeId;
	}

	private final Set<JID> clusterNodes;

	private final Map<String, NodeInfo> nodesMap = new HashMap<String, NodeInfo>();

	private final Random random = new SecureRandom();

	public ClusterNodeMap(Set<JID> cluster_nodes) {
		this.clusterNodes = cluster_nodes;
	}

	public void addPubSubNode(final String nodeName) {
		nodesMap.put(nodeName, new NodeInfo());
	}

	public void addPubSubNode(final String[] nodeNames) {
		if (nodeNames != null) {
			for (String string : nodeNames) {
				addPubSubNode(string);
			}
		}
	}

	public void assign(final String clusterNodeId, final String pubSubNodeName) {
		NodeInfo i = this.nodesMap.get(pubSubNodeName);
		if (i == null) {
			i = new NodeInfo();
			this.nodesMap.put(pubSubNodeName, i);
		}
		i.clusterNodeId = clusterNodeId;
	}

	public String getClusterNodeId(final String pubsubNodeName) {
		NodeInfo i = this.nodesMap.get(pubsubNodeName);
		if (i != null) {
			return i.clusterNodeId;
		} else {
			return null;
		}
	}

	public Map<String, Integer> getClusterNodesLoad() {
		final Map<String, Integer> nodeLoad = new HashMap<String, Integer>();
		// init
		for (JID n : this.clusterNodes) {
			nodeLoad.put(n.toString(), new Integer(0));
		}

		// counting
		for (Entry<String, NodeInfo> entry : this.nodesMap.entrySet()) {
			if (entry.getValue().clusterNodeId == null)
				continue;
			Integer a = nodeLoad.get(entry.getValue().clusterNodeId);
			if (a != null) {
				a++;
				nodeLoad.put(entry.getValue().clusterNodeId, a);
			}
		}
		return nodeLoad;
	}

	/**
	 * Stupid name, but important method. This meathod realize Load-Balancing
	 *
	 * @return Name of (usual) less loaded cluster node
	 */
	public String getNewOwnerOfNode(final String nodeName) {
		// calculating current node load
		final Map<String, Integer> nodeLoad = getClusterNodesLoad();

		Integer minCount = null;
		for (Integer i : nodeLoad.values()) {
			minCount = minCount == null || minCount > i ? i : minCount;
		}

		List<String> shortList = new ArrayList<String>();

		for (Entry<String, Integer> entry : nodeLoad.entrySet()) {
			if (entry.getValue().equals(minCount)) {
				shortList.add(entry.getKey());
			}
		}

		int r = this.random.nextInt(shortList.size());

		return shortList.get(r);
	}

}
