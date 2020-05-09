/*
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
package tigase.pubsub.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.eventbus.EventBus;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.HashSet;
import java.util.logging.Level;

@Bean(name = "nodeConfigModule", parent = PubSubComponent.class, active = true)
public class NodeConfigModule
		extends AbstractConfigCreateNode {

	private static final Criteria CRIT_CONFIG = ElementCriteria.name("iq")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner"))
			.add(ElementCriteria.name("configure"));
	@Inject
	private EventBus eventBus;
	@Inject
	private PublishItemModule publishModule;

	protected static String[] diff(String[] a, String[] b) {
		HashSet<String> r = new HashSet<String>();

		for (String $a : a) {
			r.add($a);
		}
		for (String $a : b) {
			r.add($a);
		}
		for (String $a : b) {
			r.remove($a);
		}

		return r.toArray(new String[]{});
	}

	public static void parseConf(final AbstractNodeConfig conf, final Element configure, final IPubSubConfig config)
			throws PubSubException {
		Element x = configure.getChild("x", "jabber:x:data");
		Form foo = new Form(x);

		if ((x != null) && "submit".equals(x.getAttributeStaticStr("type"))) {
			for (Field field : conf.getForm().getAllFields()) {
				final String var = field.getVar();
				Field cf = foo.get(var);

				if (cf != null) {
					if (!config.isSendLastPublishedItemOnPresence() && "pubsub#send_last_published_item".equals(var)) {
						if (SendLastPublishedItem.on_sub_and_presence.name().equals(cf.getValue())) {
							throw new PubSubException(Authorization.NOT_ACCEPTABLE,
													  "Requested on_sub_and_presence mode for sending last published item is disabled.");
						}
					}

					field.setValues(cf.getValues());
				}
			}
		}
	}

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#config-node"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CONFIG;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			final BareJID toJid = packet.getStanzaTo().getBareJID();
			final Element element = packet.getElement();
			final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			final Element configure = pubSub.getChild("configure");
			final String nodeName = configure.getAttributeStaticStr("node");
			final StanzaType type = packet.getType();
			final String id = element.getAttributeStaticStr("id");

			if (nodeName == null) {
				throw new PubSubException(element, Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
			}

			final AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			JID jid = packet.getStanzaFrom();

			pubSubLogic.checkPermission(toJid, nodeName, jid, PubSubLogic.Action.manageNode);

			// TODO 8.2.3.4 No Configuration Options

			// final Element result = createResultIQ(element);
			final Packet result = packet.okResult((Element) null, 0);

			if (type == StanzaType.get) {
				Element rPubSub = new Element("pubsub", new String[]{"xmlns"},
											  new String[]{"http://jabber.org/protocol/pubsub#owner"});
				Element rConfigure = new Element("configure", new String[]{"node"}, new String[]{nodeName});
				if (nodeConfig instanceof CollectionNodeConfig) {
					((CollectionNodeConfig) nodeConfig).setChildren(getRepository().getChildNodes(toJid, nodeName));
				}
				Element f = nodeConfig.getFormElement();

				rConfigure.addChild(f);
				rPubSub.addChild(rConfigure);
				result.getElement().addChild(rPubSub);
			} else if (type == StanzaType.set) {
				String[] children = nodeConfig instanceof CollectionNodeConfig
									? getRepository().getChildNodes(toJid, nodeName)
									: new String[0];
				final String collectionOld = (nodeConfig.getCollection() == null) ? "" : nodeConfig.getCollection();

				parseConf(nodeConfig, configure, config);
				if (!collectionOld.equals(nodeConfig.getCollection())) {
					if (collectionOld.equals("")) {
						pubSubLogic.checkPermission(toJid, nodeConfig.getCollection(), jid, PubSubLogic.Action.manageNode);

						AbstractNodeConfig colNodeConfig = getRepository().getNodeConfig(toJid,
																						 nodeConfig.getCollection());

						if (!(colNodeConfig instanceof CollectionNodeConfig)) {
							throw new PubSubException(Authorization.NOT_ALLOWED,
													  "(#1) Node '" + nodeConfig.getCollection() +
															  "' is not collection node");
						}
						getRepository().update(toJid, colNodeConfig.getNodeName(), colNodeConfig);
						getRepository().removeFromRootCollection(toJid, nodeName);

						Element associateNotification = createAssociateNotification(colNodeConfig.getNodeName(),
																					nodeName);

						publishModule.generateNodeNotifications(packet.getStanzaTo().getBareJID(), nodeName, associateNotification, null, false);
					}
					if (nodeConfig.getCollection().equals("")) {
						AbstractNodeConfig colNodeConfig = getRepository().getNodeConfig(toJid, collectionOld);

						if ((colNodeConfig != null) && (colNodeConfig instanceof CollectionNodeConfig)) {
							getRepository().update(toJid, colNodeConfig.getNodeName(), colNodeConfig);
						}
						getRepository().addToRootCollection(toJid, nodeName);

						Element disassociateNotification = createDisassociateNotification(collectionOld, nodeName);

						publishModule.generateNodeNotifications(packet.getStanzaTo().getBareJID(), nodeName, disassociateNotification, null, false);
					}
				}
				if (nodeConfig instanceof CollectionNodeConfig) {
					String[] newChildren = nodeConfig.getChildren();
					if (newChildren == null) {
						newChildren = new String[0];
					}
					final String[] removedChildNodes = diff(children, newChildren);
					final String[] addedChildNodes = diff(newChildren, children);

					for (String ann : addedChildNodes) {
						AbstractNodeConfig nc = getRepository().getNodeConfig(toJid, ann);

						if (nc == null) {
							throw new PubSubException(Authorization.ITEM_NOT_FOUND,
													  "(#2) Node '" + ann + "' doesn't exists");
						}
						if (nc.getCollection().equals("")) {
							getRepository().removeFromRootCollection(toJid, nc.getNodeName());
						}
						nc.setCollection(nodeName);
						getRepository().update(toJid, nc.getNodeName(), nc);

						Element associateNotification = createAssociateNotification(nodeName, ann);

						publishModule.generateNodeNotifications(packet.getStanzaTo().getBareJID(), nodeName, associateNotification, null, false);
					}
					for (String rnn : removedChildNodes) {
						AbstractNodeConfig nc = getRepository().getNodeConfig(toJid, rnn);

						if (nc != null) {
							nc.setCollection("");
							getRepository().update(toJid, nc.getNodeName(), nc);
						}
						if ((rnn != null) && (rnn.length() != 0)) {
							Element disassociateNotification = createDisassociateNotification(nodeName, rnn);

							publishModule.generateNodeNotifications(packet.getStanzaTo().getBareJID(), nodeName, disassociateNotification, null, false);
						}
					}
				}
				getRepository().update(toJid, nodeName, nodeConfig);

				eventBus.fire(new NodeConfigurationChangedEvent(config.getComponentName(), toJid, nodeName));

				if (nodeConfig.isNotify_config()) {
					Element configuration = new Element("configuration", new String[]{"node"}, new String[]{nodeName});

					publishModule.generateNodeNotifications(packet.getStanzaTo().getBareJID(), nodeName, configuration, null, false);
				}
			} else {
				throw new PubSubException(element, Authorization.BAD_REQUEST);
			}

			// we are sending ok result after applying all changes and after
			// sending notifications, is it ok? XEP-0060 is not specific about
			// this
			packetWriter.write(result);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing node config packet", e);

			throw new RuntimeException(e);
		}
	}

	protected boolean isIn(String node, String[] children) {
		if (node == null | children == null) {
			return false;
		}
		for (String x : children) {
			if (x.equals(node)) {
				return true;
			}
		}

		return false;
	}

	private Element createAssociateNotification(final String collectionNodeName, String associatedNodeName) {
		Element colE = new Element("collection", new String[]{"node"}, new String[]{collectionNodeName});

		colE.addChild(new Element("associate", new String[]{"node"}, new String[]{associatedNodeName}));

		return colE;
	}

	private Element createDisassociateNotification(final String collectionNodeName, String disassociatedNodeName) {
		Element colE = new Element("collection", new String[]{"node"}, new String[]{collectionNodeName});

		colE.addChild(new Element("disassociate", new String[]{"node"}, new String[]{disassociatedNodeName}));

		return colE;
	}

	public static class NodeConfigurationChangedEvent {

		public final String componentName;
		public final String node;
		public final BareJID serviceJid;

		public NodeConfigurationChangedEvent(String componentName, BareJID serviceJid, String node) {
			this.componentName = componentName;
			this.serviceJid = serviceJid;
			this.node = node;
		}

	}

}
