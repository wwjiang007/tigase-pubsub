/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Artur Hefczyc" <artur.hefczyc@tigase.org>
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
 *
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */

/*
 Delete PubSub node

 AS:Description: Delete node
 AS:CommandId: delete-node
 AS:Component: pubsub
 */

package tigase.admin


def NODE = "node"

IPubSubRepository pubsubRepository = component.pubsubRepository

def p = (Packet)packet
def admins = (Set)adminsSet
def stanzaFromBare = p.getStanzaFrom().getBareJID()
def isServiceAdmin = admins.contains(stanzaFromBare)

def node = Command.getFieldValue(packet, NODE)

if (node == null) {
	def result = p.commandResult(Command.DataType.form);

	Command.addTitle(result, "Deleting a node")
	Command.addInstructions(result, "Fill out this form to delete a node.")

	Command.addFieldValue(result, NODE, node ?: "", "text-single",
			"The node to delete")	

	return result
}

def result = p.commandResult(Command.DataType.result)
try {
	if (isServiceAdmin) {
		def toJid = p.getStanzaTo().getBareJID();
		
		AbstractNodeConfig nodeConfig = pubsubRepository.getNodeConfig(toJid, node);

		if (nodeConfig == null) {
			throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
		}

		List<Packet> results = [];
		
		if (nodeConfig.isNotify_config()) {
			def nodeSubscriptions = pubsubRepository.getNodeSubscriptions(toJid, node);
			Element del = new Element("delete");
			del.setAttribute("node", node);

			results.addAll(component.publishNodeModule.prepareNotification(del, p.getStanzaTo(), node,
							nodeConfig, nodeAffiliations, nodeSubscriptions));
		}		


		final String parentNodeName                 = nodeConfig.getCollection();
		CollectionNodeConfig parentCollectionConfig = null;
		
		if ((parentNodeName != null) &&!parentNodeName.equals("")) {
			parentCollectionConfig =
			(CollectionNodeConfig) pubsubRepository.getNodeConfig(toJid, parentNodeName);
			if (parentCollectionConfig != null) {
				parentCollectionConfig.removeChildren(node);
			}
		} else {
			pubsubRepository.removeFromRootCollection(toJid, node);
		}
		if (nodeConfig instanceof CollectionNodeConfig) {
			CollectionNodeConfig cnc     = (CollectionNodeConfig) nodeConfig;
			final String[] childrenNodes = cnc.getChildren();
			
			if ((childrenNodes != null) && (childrenNodes.length > 0)) {
				for (String childNodeName : childrenNodes) {
					AbstractNodeConfig childNodeConfig = pubsubRepository.getNodeConfig(toJid, childNodeName);
					
					if (childNodeConfig != null) {
						childNodeConfig.setCollection(parentNodeName);
						pubsubRepository.update(toJid, childNodeName, childNodeConfig);
					}
					if (parentCollectionConfig != null) {
						parentCollectionConfig.addChildren(childNodeName);
					} else {
						pubsubRepository.addToRootCollection(toJid, childNodeName);
					}
				}
			}
		}
		if (parentCollectionConfig != null) {
			pubsubRepository.update(toJid, parentNodeName, parentCollectionConfig);
		}
		
		pubsubRepository.deleteNode(toJid, node);
			
		component.nodeDeleteModule.fireOnNodeDeleted(node);
		
		results.each { packet ->
			component.addOutPacket(packet);
		}
		
		Command.addTextField(result, "Note", "Operation successful");
	} else {
		Command.addTextField(result, "Error", "You do not have enough permissions to delete a node.");
	}
} catch (PubSubException ex) {
	Command.addTextField(result, "Error", ex.getMessage())
} catch (TigaseDBException ex) {
	Command.addTextField(result, "Note", "Problem accessing database, node not deleted.");
}

return result
