/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.modules;

import java.util.List;
import java.util.UUID;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.PubSubRepository;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * Case 8.1.2
 * 
 * @author bmalkow
 * 
 */
public class NodeCreateModule extends AbstractConfigCreateNode {

	public NodeCreateModule(PubSubConfig config, PubSubRepository pubsubRepository, LeafNodeConfig defaultNodeConfig) {
		super(config, pubsubRepository, defaultNodeConfig);
	}

	private static final Criteria CRIT_CREATE = ElementCriteria.nameType("iq", "set").add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("create"));

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#create-and-configure",
				"http://jabber.org/protocol/pubsub#create-nodes", "http://jabber.org/protocol/pubsub#instant-nodes" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CREATE;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element create = pubSub.getChild("create");
		final Element configure = pubSub.getChild("configure");

		String nodeName = create.getAttribute("node");
		try {
			boolean instantNode = nodeName == null;
			if (instantNode) {
				nodeName = UUID.randomUUID().toString().replaceAll("-", "");
			}

			NodeType nodeType = repository.getNodeType(nodeName);
			if (nodeType != null) {
				throw new PubSubException(element, Authorization.CONFLICT);
			}

			nodeType = NodeType.leaf;
			String collection = null;
			LeafNodeConfig nodeConfig = defaultNodeConfig.clone();
			if (configure != null) {
				Element x = configure.getChild("x", "jabber:x:data");
				if (x != null && "submit".equals(x.getAttribute("type"))) {
					for (Element field : x.getChildren()) {
						if ("field".equals(field.getName())) {
							final String var = field.getAttribute("var");
							String val = null;
							Element value = field.getChild("value");
							if (value != null) {
								val = value.getCData();
							}
							if ("pubsub#node_type".equals(var)) {
								nodeType = NodeType.valueOf(val);
							} else if ("pubsub#collection".equals(var)) {
								collection = val;
							} else
								nodeConfig.setValue(var, val);
						}
					}
				}
			}

			if (collection != null) {
				NodeType colNodeType = repository.getNodeType(collection);
				if (colNodeType == null) {
					throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
				} else if (colNodeType == NodeType.leaf) {
					throw new PubSubException(element, Authorization.NOT_ALLOWED);
				}
			}

			repository.createNode(nodeName, JIDUtils.getNodeID(element.getAttribute("from")), nodeConfig, nodeType, collection == null ? ""
					: collection);

			Element result = createResultIQ(element);
			if (instantNode) {
				Element ps = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });
				Element cr = new Element("create", new String[] { "node" }, new String[] { nodeName });
				ps.addChild(cr);
				result.addChild(ps);
			}
			return makeArray(result);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}
}