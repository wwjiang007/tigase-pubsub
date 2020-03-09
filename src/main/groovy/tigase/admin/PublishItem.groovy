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
/*
 Publish item to PubSub node

 AS:Description: Publish item to node
 AS:CommandId: publish-item
 AS:Component: pubsub
 */

package tigase.admin

import groovy.transform.CompileStatic
import tigase.db.TigaseDBException
import tigase.eventbus.EventBus
import tigase.kernel.core.Kernel
import tigase.pubsub.*
import tigase.pubsub.exceptions.PubSubErrorCondition
import tigase.pubsub.exceptions.PubSubException
import tigase.pubsub.modules.PublishItemModule
import tigase.pubsub.repository.IPubSubRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.util.datetime.TimestampHelper
import tigase.xml.DomBuilderHandler
import tigase.xml.Element
import tigase.xml.SingletonFactory
import tigase.xmpp.Authorization

Kernel kernel = (Kernel) kernel;
PubSubComponent component = (PubSubComponent) component
packet = (Iq) packet
eventBus = (EventBus) eventBus

@CompileStatic
Packet process(Kernel kernel, PubSubComponent component, Iq p, EventBus eventBus, Set admins) {

	def componentConfig = kernel.getInstance(PubSubConfig.class)

	IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);

	try {
		def NODE = "node"
		def ID = "item-id";
		def EXPIRE = "expire-at";
		def ENTRY = "entry";

		def stanzaFromBare = p.getStanzaFrom().getBareJID()
		def isServiceAdmin = admins.contains(stanzaFromBare)

		def node = Command.getFieldValue(p, NODE)
		def id = Command.getFieldValue(p, ID);
		def expire = Command.getFieldValue(p, EXPIRE);
		def entry = Command.getFieldValues(p, ENTRY);

		def dtf = new TimestampHelper();

		if (!node || !entry) {
			def result = p.commandResult(Command.DataType.form);

			Command.addTitle(result, "Publish item to a node")
			Command.addInstructions(result, "Fill out this form to publish item to a node.")

			Command.addFieldValue(result, NODE, node ?: "", "text-single",
								  "The node to publish to")
			Command.addFieldValue(result, ID, id ?: "", "text-single",
								  "ID of item")
			Command.addFieldValue(result, EXPIRE, expire ?: "", "text-single",
								  "Expiry date of item (XEP-0082 format)")
			Command.addFieldValue(result, ENTRY, entry ? entry.join("\n") : "", "text-multi",
								  "Entry")

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

				def nodeAffiliations = pubsubRepository.getNodeAffiliations(toJid, node);
				def nodeSubscriptions = pubsubRepository.getNodeSubscriptions(toJid, node);

				Element item = new Element("item");
                if (!id) {
                    id = UUID.randomUUID().toString()
                };
				item.setAttribute("id", id);

				if (expire) {
					try {
						def parseDateTime = dtf.parseTimestamp(expire);
						if (parseDateTime) {
							item.setAttribute(EXPIRE, dtf.format(parseDateTime));
						}
					} catch (java.text.ParseException e) {
						throw new PubSubException(Authorization.BAD_REQUEST,
												  "Expiration date " + expire + " is malformed.");
					}
				}

				def data = entry.join("\n")
				def chars = ((String) data).toCharArray();

				def handler = new DomBuilderHandler();
				def parser = SingletonFactory.getParserInstance();
				parser.parse(handler, chars, 0, chars.length);
				item.addChildren((LinkedList<Element>) handler.getParsedElements());

				def publishNodeModule = kernel.getInstance(PublishItemModule.class);
				publishNodeModule.generateNotifications(p.getStanzaTo().getBareJID(), node, [item], null, false);
				
				def leafNodeConfig = (LeafNodeConfig) nodeConfig;
				if (leafNodeConfig.isPersistItem()) {
					def nodeItems = pubsubRepository.getNodeItems(toJid, node);

					nodeItems.writeItem(id, p.getAttributeStaticStr("from"), item, null);

					if (leafNodeConfig.getMaxItems() != null) {
						publishNodeModule.trimItems(toJid, node, leafNodeConfig.getMaxItems(), leafNodeConfig.getCollectionItemsOrdering());
					}
				}

				def itemsToSend = [ ];
				itemsToSend += item;

				eventBus.fire(
						new PublishItemModule.ItemPublishedEvent(p.getStanzaTo().getBareJID(), node, p.getAttributeStaticStr("from"), null, itemsToSend));

				Command.addTextField(result, "Note", "Operation successful");
				Command.addFieldValue(result, "item-id", "" + id, "fixed", "Item ID")
			} else {
				//Command.addTextField(result, "Error", "You do not have enough permissions to publish item to a node.");
				throw new PubSubException(Authorization.FORBIDDEN,
										  "You do not have enough " + "permissions to publish item to a node.");
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
                if (pubsubCondition) {
                    errorEl.addChild(pubsubCondition)
                };
				result.getElement().addChild(errorEl);
			}
		} catch (TigaseDBException ex) {
			Command.addTextField(result, "Note", "Problem accessing database, item not published to node.");
		}

		return result;
	} catch (Exception ex) {
		ex.printStackTrace();
		return null;
	}
}

return process(kernel, component, packet, eventBus, (Set) adminsSet)
