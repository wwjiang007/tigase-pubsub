/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2016 "Artur Hefczyc" <artur.hefczyc@tigase.org>
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
 */

/*
 Delete item from PubSub node

 AS:Description: Delete item from node
 AS:CommandId: delete-item
 AS:Component: pubsub
 */

package tigase.admin

import groovy.transform.CompileStatic
import tigase.db.TigaseDBException
import tigase.eventbus.EventBus
import tigase.kernel.core.Kernel
import tigase.pubsub.LeafNodeConfig
import tigase.pubsub.NodeType
import tigase.pubsub.PubSubComponent
import tigase.pubsub.PubSubConfig
import tigase.pubsub.exceptions.PubSubErrorCondition
import tigase.pubsub.exceptions.PubSubException
import tigase.pubsub.modules.PublishItemModule
import tigase.pubsub.modules.RetractItemModule
import tigase.pubsub.repository.IPubSubRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.xml.Element
import tigase.xmpp.Authorization

Kernel kernel = (Kernel) kernel;
PubSubComponent component = (PubSubComponent) component
packet = (Iq) packet
eventBus = (EventBus) eventBus

@CompileStatic
Packet process(Kernel kernel, PubSubComponent component, Iq p, EventBus eventBus, Set admins) {

    try {
        def NODE = "node"
        def ID = "item-id";

        def componentConfig = kernel.getInstance(PubSubConfig.class)
        IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);

        def stanzaFromBare = p.getStanzaFrom().getBareJID()
        def isServiceAdmin = admins.contains(stanzaFromBare)

        def node = Command.getFieldValue(p, NODE);
        def id = Command.getFieldValue(p, ID);

        if (!node || !id) {
            def result = p.commandResult(Command.DataType.form);

            Command.addTitle(result, "Delete item from a node")
            Command.addInstructions(result, "Fill out this form to delete item from a node.")

            Command.addFieldValue(result, NODE, node ?: "", "text-single",
                    "The node to delete from")
            Command.addFieldValue(result, ID, id ?: "", "text-single",
                    "ID of item")
            return result
        }

        def result = p.commandResult(Command.DataType.result)
        try {
            if (isServiceAdmin || componentConfig.isAdmin(stanzaFromBare)) {
                def toJid = p.getStanzaTo().getBareJID();

                def nodeConfig = pubsubRepository.getNodeConfig(toJid, node);
                if (nodeConfig == null) {
                    throw new PubSubException(Authorization.ITEM_NOT_FOUND, "Node " + node + " needs " +
                            "to be created before an item can be published.");
                }
                if (nodeConfig.getNodeType() == NodeType.collection) {
                    throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED,
                            new PubSubErrorCondition("unsupported", "publish"));
                }

                def removed = false;
                if (((LeafNodeConfig) nodeConfig).isPersistItem()) {
                    def nodeItems = pubsubRepository.getNodeItems(toJid, node);
                    if (nodeItems.getItemCreationDate(id)) {
                        Element notification = new Element("retract", ["id"] as String[], [id] as String[]);

                        removed = true;
                        nodeItems.deleteItem(id);
                        eventBus.fire(new RetractItemModule.ItemRetractedEvent(toJid, node, notification));

                        def publishNodeModule = kernel.getInstance(PublishItemModule.class);
                        publishNodeModule.sendNotifications(p.getStanzaTo().getBareJID(), node, [notification]);
                    }
                }

                if (removed) {
                    Command.addTextField(result, "Note", "Operation successful");
                    Command.addFieldValue(result, "item-id", "" + id, "fixed", "Item ID");
                } else {
                    throw new PubSubException(Authorization.ITEM_NOT_FOUND, "Item with ID " + id + " was not found");
                }
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
            Command.addTextField(result, "Note", "Problem accessing database, item not published to node.");
        }

        return result

    } catch (Exception ex) {
        ex.printStackTrace();
        return null;
    }
}

return process(kernel, component, packet, eventBus, (Set) adminsSet)