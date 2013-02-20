/*
 * DiscoverItemsModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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



package tigase.pubsub.modules;

//~--- non-JDK imports --------------------------------------------------------

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;

import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.Utils;

import tigase.xml.Element;

import tigase.xmpp.Authorization;

//~--- JDK imports ------------------------------------------------------------

import java.util.List;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/20
 * @author         Enter your name here...
 */
public class DiscoverItemsModule
				extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq",
																				 "get").add(ElementCriteria.name("query",
																					 "http://jabber.org/protocol/disco#items"));

	//~--- fields ---------------------------------------------------------------

	private final AdHocConfigCommandModule adHocCommandsModule;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param pubsubRepository
	 * @param adCommandModule
	 */
	public DiscoverItemsModule(PubSubConfig config, IPubSubRepository pubsubRepository,
														 AdHocConfigCommandModule adCommandModule) {
		super(config, pubsubRepository);
		this.adHocCommandsModule = adCommandModule;
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param element
	 * @param elementWriter
	 *
	 * @return
	 *
	 * @throws PubSubException
	 */
	@Override
	public List<Element> process(Element element, ElementWriter elementWriter)
					throws PubSubException {
		try {
			final Element query = element.getChild("query",
															"http://jabber.org/protocol/disco#items");
			final String nodeName  = query.getAttributeStaticStr("node");
			final String senderJid = element.getAttributeStaticStr("from");
			Element resultIq       = createResultIQ(element);
			Element resultQuery    = new Element("query", new String[] { "xmlns" },
																 new String[] {
																	 "http://jabber.org/protocol/disco#items" });

			resultIq.addChild(resultQuery);
			if ("http://jabber.org/protocol/commands".equals(nodeName)) {
				List<Element> commandList =
					this.adHocCommandsModule.getCommandListItems(senderJid,
						element.getAttributeStaticStr("to"));

				if (commandList != null) {
					for (Element item : commandList) {
						resultQuery.addChild(item);
					}
				}
			} else {
				log.finest("Asking about Items of node " + nodeName);

				AbstractNodeConfig nodeConfig = (nodeName == null)
																				? null
																				: repository.getNodeConfig(nodeName);
				String[] nodes;

				if ((nodeName == null) ||
						((nodeConfig != null) && (nodeConfig.getNodeType() == NodeType.collection))) {
					String parentName;

					if (nodeName == null) {
						parentName = "";
						nodes      = repository.getRootCollection();
					} else {
						parentName = nodeName;
						nodes      = nodeConfig.getChildren();
					}

					// = this.repository.getNodesList();
					if (nodes != null) {
						for (String node : nodes) {
							AbstractNodeConfig childNodeConfig = this.repository.getNodeConfig(node);

							if (childNodeConfig != null) {
								boolean allowed = ((senderJid == null) || (childNodeConfig == null))
																	? true
																	: Utils.isAllowedDomain(senderJid,
																		childNodeConfig.getDomains());
								String collection = childNodeConfig.getCollection();

								if (allowed) {
									String name = childNodeConfig.getTitle();

									name = ((name == null) || (name.length() == 0))
												 ? node
												 : name;

									Element item = new Element("item", new String[] { "jid", "node",
													"name" }, new String[] { element.getAttributeStaticStr("to"),
																									 node, name });

									if (parentName.equals(collection)) {
										resultQuery.addChild(item);
									}
								} else {
									log.fine("User " + senderJid + " not allowed to see node '" + node +
													 "'");
								}
							}
						}
					}
				} else {
					boolean allowed = ((senderJid == null) || (nodeConfig == null))
														? true
														: Utils.isAllowedDomain(senderJid, nodeConfig.getDomains());

					if (!allowed) {
						throw new PubSubException(Authorization.FORBIDDEN);
					}
					resultQuery.addAttribute("node", nodeName);

					IItems items     = repository.getNodeItems(nodeName);
					String[] itemsId = items.getItemsIds();

					if (itemsId != null) {
						for (String itemId : itemsId) {
							resultQuery.addChild(new Element("item", new String[] { "jid", "name" },
																							 new String[] {
																								 element.getAttributeStaticStr("to"),
																								 itemId }));
						}
					}
				}
			}

			return makeArray(resultIq);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
