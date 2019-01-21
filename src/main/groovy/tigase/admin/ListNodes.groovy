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
 List PubSub nodes

 AS:Description: List nodes
 AS:CommandId: list-nodes
 AS:Component: pubsub
 */

package tigase.admin

import groovy.transform.CompileStatic
import tigase.eventbus.EventBus
import tigase.kernel.core.Kernel
import tigase.pubsub.PubSubComponent
import tigase.pubsub.PubSubConfig
import tigase.pubsub.exceptions.PubSubException
import tigase.pubsub.repository.IPubSubRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.xml.Element
import tigase.xmpp.Authorization;

Kernel kernel = (Kernel) kernel;
PubSubComponent component = (PubSubComponent) component
packet = (Iq) packet
eventBus = (EventBus) eventBus

@CompileStatic
Packet process(Kernel kernel, PubSubComponent component, Iq p, EventBus eventBus, Set admins) {

	def componentConfig = kernel.getInstance(PubSubConfig.class)

	IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);

	def stanzaFromBare = p.getStanzaFrom().getBareJID()
	def isServiceAdmin = admins.contains(stanzaFromBare)

	def result = p.commandResult(Command.DataType.result);
	Command.addTitle(result, "List of available nodes");

	try {
		if (isServiceAdmin || componentConfig.isAdmin(stanzaFromBare)) {
			def serviceJid = p.getStanzaTo().getBareJID();
			try {
				def nodes = pubsubRepository.getRootCollection(serviceJid);

				Command.addFieldMultiValue(result, "nodes", nodes as List);
				result.getElement().
						getChild('command').
						getChild('x').
						getChildren().
						find { e -> e.getAttribute("var") == "nodes" }?.
						setAttribute("label", "Nodes");
			} catch (tigase.pubsub.repository.cached.CachedPubSubRepository.RootCollectionSet.IllegalStateException ex) {
				throw new PubSubException(Authorization.RESOURCE_CONSTRAINT);
			}
		} else {
			throw new PubSubException(Authorization.FORBIDDEN,
									  "You do not have enough " + "permissions to list available nodes.");
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
	}
	return result;
}

return process(kernel, component, packet, eventBus, (Set) adminsSet)
