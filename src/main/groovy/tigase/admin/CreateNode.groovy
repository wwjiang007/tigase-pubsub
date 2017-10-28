/*
 * CreateNode.groovy
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

/*
 Create PubSub node

 AS:Description: Create node
 AS:CommandId: create-node
 AS:Component: pubsub
 */

package tigase.admin

import groovy.transform.CompileStatic
import tigase.db.TigaseDBException
import tigase.eventbus.EventBus
import tigase.kernel.core.Kernel
import tigase.pubsub.*
import tigase.pubsub.exceptions.PubSubException
import tigase.pubsub.modules.NodeCreateModule
import tigase.pubsub.modules.PublishItemModule
import tigase.pubsub.repository.IPubSubRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.xml.Element
import tigase.xmpp.Authorization
import tigase.xmpp.jid.BareJID;

Kernel kernel = (Kernel) kernel;
PubSubComponent component = (PubSubComponent) component
packet = (Iq) packet
eventBus = (EventBus) eventBus

@CompileStatic
Packet process(Kernel kernel, PubSubComponent component, Iq p, EventBus eventBus, Set admins) {
    def NODE = "node"
    def OWNER = "owner";

    def componentConfig = kernel.getInstance(PubSubConfig.class)

    IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);

    def stanzaFromBare = p.getStanzaFrom().getBareJID()
    def isServiceAdmin = admins.contains(stanzaFromBare)

    def node = Command.getFieldValue(p, NODE)
    def owner = Command.getFieldValue(p, OWNER);

    if (node == null) {
        def result = p.commandResult(Command.DataType.form);

        Command.addTitle(result, "Creating a node")
        Command.addInstructions(result, "Fill out this form to create a node.")

        Command.addFieldValue(result, NODE, node ? node : "", "text-single",
                "The node to create")
        Command.addFieldValue(result, OWNER, owner ? owner : "", "jid-single",
                "Owner JID")

        def nodeConfig = new LeafNodeConfig(null);
        List<Element> fields = nodeConfig.getFormElement().getChildren();
        Element x = Command.getData(result, "x", "jabber:x:data");
        x.addChildren(fields);

        return result
    }

    def result = p.commandResult(Command.DataType.result)
    try {
        if (isServiceAdmin || componentConfig.isAdmin(stanzaFromBare)) {
            def toJid = p.getStanzaTo().getBareJID();

            def nodeConfig = pubsubRepository.getNodeConfig(toJid, node);

            if (nodeConfig != null) {
                throw new PubSubException(Authorization.CONFLICT, "Node " + node + " already exists");
            }

            def nodeTypeStr = Command.getFieldValue(p, "pubsub#node_type");
            def nodeType = nodeTypeStr ? NodeType.valueOf(nodeTypeStr) : NodeType.leaf;

            def defaultNodeConfig = (DefaultNodeConfig) kernel.getInstance("defaultNodeConfig");

            nodeConfig = (nodeType == NodeType.leaf) ? new LeafNodeConfig(node, defaultNodeConfig) : new CollectionNodeConfig(node);

            Command.getData(p, "x", "jabber:x:data").getChildren().each { fieldEl ->
                def var = fieldEl.getAttribute("var");
                def field = nodeConfig.getForm().get(var);
                def value = fieldEl.getChildCData("/field/value")
                if (!field) return;
                if (field.getType().name().endsWith("-multi")) {
                    nodeConfig.setValues(field.getVar(), value.tokenize() as String[]);
                } else {
                    nodeConfig.setValue(field.getVar(), value);
                }
            };

            def collection = nodeConfig.getCollection();
            CollectionNodeConfig colNodeConfig = null;

            if (collection != '') {
                AbstractNodeConfig absNodeConfig = pubsubRepository.getNodeConfig(toJid, collection);

                if (absNodeConfig == null) {
                    throw new PubSubException(p.getElement(), Authorization.ITEM_NOT_FOUND);
                } else if (absNodeConfig.getNodeType() == NodeType.leaf) {
                    throw new PubSubException(p.getElement(), Authorization.NOT_ALLOWED);
                }
                colNodeConfig = (CollectionNodeConfig) absNodeConfig;
            }

            def ownerJid = (!owner) ? p.getStanzaFrom().getBareJID() : BareJID.bareJIDInstance(owner);
            pubsubRepository.createNode(toJid, node, ownerJid,
                    nodeConfig, nodeType, collection);

            def nodeaAffiliations = pubsubRepository.getNodeAffiliations(toJid, node);
            nodeaAffiliations.addAffiliation(ownerJid, Affiliation.owner);

            def nodeSubscriptions = pubsubRepository.getNodeSubscriptions(toJid, node);
            nodeSubscriptions.addSubscriberJid(ownerJid, Subscription.subscribed);

            pubsubRepository.update(toJid, node, nodeaAffiliations);
            pubsubRepository.update(toJid, node, nodeSubscriptions);

            if (colNodeConfig == null) {
                pubsubRepository.addToRootCollection(toJid, node);
            } else {
                pubsubRepository.update(toJid, collection, colNodeConfig);
            }

            NodeCreateModule.NodeCreatedEvent event = new NodeCreateModule.NodeCreatedEvent(toJid, node);
            eventBus.fire(event);

            if (collection != '') {
                def colNodeSubscriptions =
                        pubsubRepository.getNodeSubscriptions(toJid, collection);
                def colNodeAffiliations =
                        pubsubRepository.getNodeAffiliations(toJid, collection);
                Element colE = new Element("collection");
                colE.setAttribute("node", collection);
                Element associateEl = new Element("associate");
                associateEl.setAttribute("node", node);
                colE.addChild(associateEl);

                def publishNodeModule = kernel.getInstance(PublishItemModule.class);
                publishNodeModule.sendNotifications(colE,
                        p.getStanzaTo(), collection, nodeConfig,
                        colNodeAffiliations, colNodeSubscriptions);
            }

            Command.addTextField(result, "Note", "Operation successful");
        } else {
            //Command.addTextField(result, "Error", "You do not have enough permissions to publish item to a node.");
            throw new PubSubException(Authorization.FORBIDDEN, "You do not have enough " +
                    "permissions to publish item to a node.");
        }
    } catch (PubSubException ex) {
        Command.addTextField(result, "Error", ex.getMessage())
        if (ex.getErrorCondition()) {
            def error = ex.getErrorCondition();
            Element errorEl = new Element("error");
            errorEl.setAttribute("type", error.getErrorType());
            Element conditionEl = new Element(error.getCondition(), ex.getMessage());
            conditionEl.setXMLNS(Packet.ERROR_NS);
            errorEl.addChild(conditionEl);
            Element pubsubCondition = ex.pubSubErrorCondition?.getElement();
            if (pubsubCondition)
                errorEl.addChild(pubsubCondition);
            result.getElement().addChild(errorEl);
        }
    } catch (TigaseDBException ex) {
        Command.addTextField(result, "Note", "Problem accessing database, node not created.");
    }

    return result
}

return process(kernel, component, packet, eventBus, (Set) adminsSet)
