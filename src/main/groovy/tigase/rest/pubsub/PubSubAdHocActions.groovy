/*
 * PubSubAdHocActions.groovy
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

package tigase.rest.pubsub

import tigase.http.coders.JsonCoder
import tigase.http.rest.Service
import tigase.server.Iq

import tigase.server.Packet
import tigase.xml.DomBuilderHandler
import tigase.xml.Element
import tigase.xml.SingletonFactory
import tigase.xml.XMLUtils

import javax.servlet.http.HttpServletRequest
/**
 * Class implements generic support for PubSub ad-hoc commands*/
class PubSubActionsHandler
		extends tigase.http.rest.Handler {

	def TIMEOUT = 30 * 1000;

	def COMMAND_XMLNS = "http://jabber.org/protocol/commands";
	def DATA_XMLNS = "jabber:x:data";
	def DISCO_ITEMS_XMLNS = "http://jabber.org/protocol/disco#items";

	public PubSubActionsHandler() {
		description = [ regex: "/{pubsub_jid}/{adhoc_command_node}",
						GET  : [ info       : 'Retrieve PubSub adhoc command fields',
								 description: """This is simplified version of adhoc command for PubSub component, which allows for easier management of PubSub component nodes and items than default adhoc command REST API.
As part of url you need to pass PubSub component jid as {pubsub_jid} parameter and adhoc command node as {adhoc_command_node} for which you wish to retrieve list of fileds for.
Retrieved XML after filling it with proper data and replacing external XML element name from result to data may be passed as content for POST request to execute this command with passed parameters for command.
""" ],
						POST : [ info       : 'Execute PubSub adhoc command',
								 description: """This is simplified version of adhoc command for PubSub component, which allows for easier management of PubSub component nodes and items than default adhoc command REST API.
As part of url you need to pass PubSub component jid as {pubsub_jid} parameter and adhoc command node as {adhoc_command_node} for which you wish to execute.
For a content of a HTTP POST request you need to pass filled XML data retrieved using GET method with external element result name changed in data.
""" ] ];
		regex = /\/(?:([^@\/]+)@){0,1}([^@\/]+)\/([^\/]+)/
		isAsync = true
		decodeContent = false;

		execGet = { Service service, callback, localPart, domain, cmd ->
			execPost(service, callback, null, localPart, domain, cmd);
		}

		execPost = { Service service, callback, HttpServletRequest request, localPart, domain, cmd ->
			//if (localPart) {
			//	localPart = localPart.substring(0,localPart.length()-1);
			//}

			def type = request?.getContentType();
			def content = decodeContent(request);

			Element iq = new Element("iq");
			iq.setXMLNS(Iq.CLIENT_XMLNS);
			iq.setAttribute("to", localPart ? "$localPart@$domain" : domain);
			iq.setAttribute("type", "set");
			iq.setAttribute("id", UUID.randomUUID().toString())

			Element command = new Element("command");
			command.setXMLNS(COMMAND_XMLNS);
			command.setAttribute("node", cmd);
			iq.addChild(command);

			if (content) {
				def data = content;
				Element x = new Element("x");
				x.setXMLNS(DATA_XMLNS);
				x.setAttribute("type", "submit");
				command.addChild(x);

				data.each { k, v ->
					Element fieldEl = new Element("field");
					fieldEl.setAttribute("var", k);
					x.addChild(fieldEl);

					if (v instanceof Collection) {
						v.each {
							fieldEl.addChild(new Element("value", XMLUtils.escape((String) it)));
						}
					} else {
					   if (v instanceof String) {
						   fieldEl.addChild(new Element("value", XMLUtils.escape((String) v)));
					   } else if (v instanceof Element) {
						   fieldEl.addChild(new Element("value", XMLUtils.escape(((Element) v).toString())));
					   } else {
						   Element payload = new Element("payload");
						   payload.setCData(new JsonCoder().encode(v));
						   fieldEl.addChild(new Element("value", XMLUtils.escape(payload.toString())));
					   }
					}
				}
			}

			service.sendPacket(new Iq(iq), TIMEOUT, { Packet result ->
				def results = type == "application/json" ? encodeResponseJson(result) : encodeResponseXml(result);
				callback(results.toString())
			});
		}
	}

	def encodeResponseJson = { result ->
		def error = result?.getElement().getChild("error");
		if (result == null || error != null) {
			return new JsonCoder().encode([ error: error.getChildren().first?.getName() ?: "internal-server-error" ])
		}

		def results = [:];

		Element command = result.getElement().getChild("command", COMMAND_XMLNS);
		def data = command.getChild("x", DATA_XMLNS);
		data.getChildren().each { el ->
			if (el.getName() == "field") {
				def var = el.getAttributeStaticStr("var");
				def values = el.getChildren().findAll { c -> c.getName() == "value" }.collect { XMLUtils.unescape(it.getCData()) };
				results[var] = el.getAttributeStaticStr("type")?.contains("-multi") ? values : (values.isEmpty() ? "" : values?.first())
			}
			else if (["title", "instructions", "note"].contains(el.getName())) {
				results[el.getName()] = el.getCData();
			}
		}

		return new JsonCoder().encode(results);
	}

	def encodeResponseXml = { result ->
		def error = result?.getElement().getChild("error");
		if (result == null || error != null) {
			return error?.toString();
		}

		Element command = result.getElement().getChild("command", COMMAND_XMLNS);
		def data = command.getChild("x", DATA_XMLNS);
		def fieldElems = data.getChildren().findAll({ it.getName() == "field" });

		Element results = new Element("result");

		["title", "instructions", "note"].each { name ->
			def el = data.getChild(name);
			if (el) {
				data.removeChild(el);
				results.addChild(el);
			}
		}

		convertFieldElements(fieldElems, results);

		return results;
	}

	def convertFieldElements = { fieldElems, results ->
		fieldElems.each { fieldEl ->
			def var = fieldEl.getAttribute("var");
			def varTmp = var.split("#");
			def elem = null;
			if (varTmp.length > 1) {
				elem = new Element(varTmp[1]);
				Element wrap = results.getChild(varTmp[0]);
				if (wrap == null) {
					wrap = new Element(varTmp[0]);
					wrap.setAttribute("prefix", "true");
					results.addChild(wrap);
				}
				wrap.addChild(elem);
			} else {
				elem = new Element(var);
				results.addChild(elem);
			}


			[ "label", "type" ].each { attr ->
				if (fieldEl.getAttribute(attr)) {
					elem.setAttribute(attr, fieldEl.getAttribute(attr));
				}
			}

			def valueElems = fieldEl.getChildren().findAll({ it.getName() == "value" });
			if (valueElems != null) {
				valueElems.each { val -> elem.addChild(new Element("value", var == "item" ? XMLUtils.unescape(val.getCData()) : val.getCData())) }
			}

			def optionElems = fieldEl.getChildren().findAll({ it.getName() == "option" });
			if (!optionElems.isEmpty()) {
				optionElems.each { optionEl ->
					Element option = new Element("option", optionEl.getChild("value").getCData());
					if (optionEl.getAttribute("label")) {
						option.setAttribute("label", optionEl.getAttribute("label"));
					}
					elem.addChild(option);
				}
			}
		}
	}

	def decodeContent(HttpServletRequest request) {
		if (request == null) {
			return null;
		}

		String content = request.getReader().getText();
		if (content == null) {
			return null;
		}

		return (request.getContentType() == "application/json") ? decodeContentJson(content) :
			   decodeContentXml(content);
	}

	def decodeContentJson(String input) {
		return new JsonCoder().decode(input);
	}

	def decodeContentXml(String input) {
		if (!input) {
			return null
		};

		def handler = new DomBuilderHandler();
		def parser = SingletonFactory.getParserInstance();

		def chars = ((String) input).toCharArray();

		parser.parse(handler, chars, 0, chars.length);

		Element data = handler.getParsedElements().get(0);
		def result = [:];

		if (data && data.getName() == "data") {
			data.getChildren().each { child ->
				if (child.getAttribute("prefix") == "true") {
					child.getChildren().each { sub ->
						result[child.name + "#" + sub.name] = sub.getCData();
					}
				} else {
					def values = child.getChildren().findAll { it.getName() == "value" }
					if (!values.isEmpty()) {
						result[child.name] = values.collect { it.getCData() };
					} else {
						if (child.getCData()) {
							result[child.name] = child.getCData();
						} else {
							result[child.name] = child.getChildren()?.first();
						}
					}
				}
			}
		}

		return result;
	}

}