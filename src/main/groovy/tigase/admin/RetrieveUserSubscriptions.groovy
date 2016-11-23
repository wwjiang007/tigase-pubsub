/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
 Retrieve user subscriptions

 AS:Description: Retrieve user subscriptions
 AS:CommandId: retrieve-user-subscriptions
 AS:Component: pubsub
 */

package tigase.admin

import groovy.transform.CompileStatic
import tigase.db.TigaseDBException
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
import tigase.xmpp.Authorization
import tigase.xmpp.BareJID

import java.util.regex.Pattern


Kernel kernel = (Kernel) kernel;
PubSubComponent component = (PubSubComponent) component
packet = (Iq) packet
eventBus = (EventBus) eventBus

@CompileStatic
Packet process(Kernel kernel, PubSubComponent component, Iq p, EventBus eventBus, Set admins) {
	def JID = "jid";
	def PATTERN = "node-pattern"

	def componentConfig = kernel.getInstance(PubSubConfig.class);
	IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);
	def stanzaFromBare = p.getStanzaFrom().getBareJID()
	def isServiceAdmin = admins.contains(stanzaFromBare)

	String jid = Command.getFieldValue(p, JID);
	String patternStr = Command.getFieldValue(p, PATTERN);

	if (jid == null) {
		def result = p.commandResult(Command.DataType.form);

		Command.addTitle(result, "Retrieve user subscriptions")
		Command.addInstructions(result, "Fill out this form to retrieve list of nodes to which user is subscribed to.")

		Command.addFieldValue(result, JID, jid ?: "", "jid-single",
							  "User JID to retrieve subscriptions")
		Command.addFieldValue(result, PATTERN, patternStr ?: "", "text-single",
							  "Regex pattern of retrieved nodes")

		return result
	}

	def result = p.commandResult(Command.DataType.result)

	try {
		if (isServiceAdmin || componentConfig.isAdmin(stanzaFromBare)) {
			def bareJid = BareJID.bareJIDInstance(jid);
			List<String> subscriptions = [ ];
			subscriptions.addAll(pubsubRepository.getUserSubscriptions(p.getStanzaTo().getBareJID(), bareJid).keySet());
			if (patternStr != null && !patternStr.isEmpty()) {
				Pattern pattern = Pattern.compile(patternStr);
				subscriptions = subscriptions.findAll { node -> pattern.matcher(node).matches() };
			}

			Command.addFieldMultiValue(result, "nodes", subscriptions as List);
			result.getElement().
					getChild('command').
					getChild('x').
					getChildren().
					find { e -> e.getAttribute("var") == "nodes" }?.
					setAttribute("label", "Nodes");
		} else {
			throw new PubSubException(Authorization.FORBIDDEN,
									  "You do not have enough " + "permissions to retrieve user subscriptions.");
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
		Command.addTextField(result, "Note", "Problem accessing database.");
	}

	return result;
}

return process(kernel, component, packet, eventBus, (Set) adminsSet)
