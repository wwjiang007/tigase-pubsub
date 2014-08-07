/*
 * RetrieveItemsModule.java
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Packet;
import tigase.util.DateTimeFormatter;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 */
public class RetrieveItemsModule extends AbstractPubSubModule {
	private class NodeItem {
		public final String cacheString;

		public final String id;

		public final String node;
		public final Date timestamp;

		public NodeItem(String nodeName, String id, Date timestamp) {
			this.node = nodeName;
			this.id = id;
			this.timestamp = timestamp;
			this.cacheString = String.format("%020d:%s:%s", timestamp.getTime(), nodeName, id);
		}

	}

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("items"));

	private static final Comparator<IItems.ItemMeta> itemsCreationDateComparator = new Comparator<IItems.ItemMeta>() {

		@Override
		public int compare(IItems.ItemMeta o1, IItems.ItemMeta o2) {
			return o1.getCreationDate().compareTo(o2.getCreationDate()) * (-1);
		}

	};

	public static void main(String[] args) {
		System.out.println();
		System.out.println(System.currentTimeMillis());
	}

	private final DateTimeFormatter dtf = new DateTimeFormatter();

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public RetrieveItemsModule(PubSubConfig config, PacketWriter packetWriter) {
		super(config, packetWriter);
	}

	private Integer asInteger(String attribute) {
		if (attribute == null) {
			return null;
		}

		return Integer.parseInt(attribute);
	}

	private void checkPermission(JID senderJid, BareJID toJid, String nodeName, AbstractNodeConfig nodeConfig)
			throws PubSubException, RepositoryException {
		if (nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND);
		}
		if ((nodeConfig.getNodeAccessModel() == AccessModel.open)
				&& !Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains())) {
			throw new PubSubException(Authorization.FORBIDDEN);
		}

		IAffiliations nodeAffiliations = this.getRepository().getNodeAffiliations(toJid, nodeName);
		UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());

		if (senderAffiliation.getAffiliation() == Affiliation.outcast) {
			throw new PubSubException(Authorization.FORBIDDEN);
		}

		ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, nodeName);
		Subscription senderSubscription = nodeSubscriptions.getSubscription(senderJid.getBareJID());

		if ((nodeConfig.getNodeAccessModel() == AccessModel.whitelist) && !senderAffiliation.getAffiliation().isRetrieveItem()) {
			throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
		} else if ((nodeConfig.getNodeAccessModel() == AccessModel.authorize)
				&& ((senderSubscription != Subscription.subscribed) || !senderAffiliation.getAffiliation().isRetrieveItem())) {
			throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_SUBSCRIBED);
		} else if (nodeConfig.getNodeAccessModel() == AccessModel.presence) {
			boolean allowed = hasSenderSubscription(senderJid.getBareJID(), nodeAffiliations, nodeSubscriptions);

			if (!allowed) {
				throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.PRESENCE_SUBSCRIPTION_REQUIRED);
			}
		} else if (nodeConfig.getNodeAccessModel() == AccessModel.roster) {
			boolean allowed = isSenderInRosterGroup(senderJid.getBareJID(), nodeConfig, nodeAffiliations, nodeSubscriptions);

			if (!allowed) {
				throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_IN_ROSTER_GROUP);
			}
		}
	}

	private List<NodeItem> extractItemsIds(final BareJID service, final String nodeName, final Element itemsElement)
			throws PubSubException, RepositoryException {
		IItems nodeItems = this.getRepository().getNodeItems(service, nodeName);

		List<Element> il = itemsElement.getChildren();

		if ((il == null) || (il.size() == 0)) {
			return null;
		}

		final List<NodeItem> result = new ArrayList<NodeItem>();

		for (Element i : il) {
			final String id = i.getAttributeStaticStr("id");

			if (!"item".equals(i.getName()) || (id == null)) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}

			Date timestamp = nodeItems.getItemCreationDate(id);

			result.add(new NodeItem(nodeName, id, timestamp));
		}

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-items" };
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

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public void process(final Packet packet) throws PubSubException {
		try {
			final BareJID toJid = packet.getStanzaTo().getBareJID();
			final Element pubsub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element items = pubsub.getChild("items");
			final String nodeName = items.getAttributeStaticStr("node");
			final JID senderJid = packet.getStanzaFrom();

			// if (nodeName == null) {
			// throw new PubSubException(Authorization.BAD_REQUEST,
			// PubSubErrorCondition.NODEID_REQUIRED);
			// }
			processNode(packet, senderJid, toJid, pubsub, items, nodeName);

		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	protected void processNode(final Packet packet, final JID senderJid, final BareJID toJid, final Element pubsub,
			final Element items, final String nodeName) throws RepositoryException, PubSubException {

		if (nodeName != null) {
			// XXX CHECK RIGHTS AUTH ETC
			AbstractNodeConfig nodeConfig = this.getRepository().getNodeConfig(toJid, nodeName);
			checkPermission(senderJid, toJid, nodeName, nodeConfig);

			if (nodeConfig instanceof CollectionNodeConfig) {
				List<IItems.ItemMeta> itemsMeta = new ArrayList<IItems.ItemMeta>();
				String[] childNodes = nodeConfig.getChildren();
				Map<String, IItems> nodeItemsCache = new HashMap<String, IItems>();
				if (childNodes != null) {
					for (String childNodeName : childNodes) {
						AbstractNodeConfig childNode = getRepository().getNodeConfig(toJid, childNodeName);
						if (childNode == null || childNode.getNodeType() != NodeType.leaf)
							continue;

						LeafNodeConfig leafChildNode = (LeafNodeConfig) childNode;
						if (!leafChildNode.isPersistItem())
							continue;

						try {
							checkPermission(senderJid, toJid, childNodeName, childNode);
							IItems childNodeItems = getRepository().getNodeItems(toJid, childNodeName);
							nodeItemsCache.put(childNodeName, childNodeItems);
							itemsMeta.addAll(childNodeItems.getItemsMeta());
						} catch (PubSubException ex) {
							// here we ignode PubSubExceptions as they are
							// permission exceptions for subnodes
						}
					}
				}

				Collections.sort(itemsMeta, itemsCreationDateComparator);

				final Element rpubsub = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub" });
				final Packet iq = packet.okResult(rpubsub, 0);

				Integer maxItems = asInteger(items.getAttributeStaticStr("max_items"));
				Integer offset = 0;

				final Element rsmGet = pubsub.getChild("set", "http://jabber.org/protocol/rsm");
				if (rsmGet != null) {
					Element m = rsmGet.getChild("max");
					if (m != null) {
						maxItems = asInteger(m.getCData());
					}
					m = rsmGet.getChild("index");
					if (m != null) {
						offset = asInteger(m.getCData());
					}
				}

				Map<String, List<Element>> nodeItemsElMap = new HashMap<String, List<Element>>();
				int idx = offset;
				int count = 0;
				String lastId = null;
				while (itemsMeta.size() > idx && (maxItems == null || count < maxItems)) {
					IItems.ItemMeta itemMeta = itemsMeta.get(idx);
					String node = itemMeta.getNode();
					List<Element> nodeItemsElems = nodeItemsElMap.get(node);
					if (nodeItemsElems == null) {
						// nodeItemsEl = new Element("items", new String[] {
						// "node" }, new String[] { node });
						nodeItemsElems = new ArrayList<Element>();
						nodeItemsElMap.put(node, nodeItemsElems);
					}

					IItems nodeItems = nodeItemsCache.get(node);
					Element item = nodeItems.getItem(itemMeta.getId());
					lastId = itemMeta.getId();
					nodeItemsElems.add(item);

					idx++;
					count++;
				}

				nodeItemsCache.clear();

				for (Map.Entry<String, List<Element>> entry : nodeItemsElMap.entrySet()) {
					Element itemsEl = new Element("items", new String[] { "node" }, new String[] { entry.getKey() });

					List<Element> itemsElems = entry.getValue();
					Collections.reverse(itemsElems);
					itemsEl.addChildren(itemsElems);

					rpubsub.addChild(itemsEl);
				}

				if (nodeItemsElMap.size() > 0) {
					final Element rsmResponse = new Element("set", new String[] { "xmlns" },
							new String[] { "http://jabber.org/protocol/rsm" });

					rsmResponse.addChild(new Element("first", itemsMeta.get(offset).getId(), new String[] { "index" },
							new String[] { String.valueOf(offset) }));
					rsmResponse.addChild(new Element("count", "" + itemsMeta.size()));
					if (lastId != null)
						rsmResponse.addChild(new Element("last", lastId));

					rpubsub.addChild(rsmResponse);
				} else {
					Element z = new Element("items");
					if (nodeName != null) {
						z.setAttribute("node", nodeName);
					}
					rpubsub.addChild(z);
				}

				packetWriter.write(iq);
				return;
			} else if ((nodeConfig instanceof LeafNodeConfig) && !((LeafNodeConfig) nodeConfig).isPersistItem()) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"persistent-items"));
			}
		}
		// final IItems nodeItems = this.getRepository().getNodeItems(toJid,
		// nodeName);

		final Element rpubsub = new Element("pubsub", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/pubsub" });
		final Element ritems = new Element("items");

		List<NodeItem> requestedId;
		if (nodeName != null) {
			ritems.setAttribute("node", nodeName);
			requestedId = extractItemsIds(toJid, nodeName, items);
			if (requestedId == null) {
				IItems nodeItems = this.getRepository().getNodeItems(toJid, nodeName);
				String[] ids = nodeItems.getItemsIds();

				if (ids != null) {
					requestedId = new ArrayList<NodeItem>();
					for (String id : ids) {
						Date cd = nodeItems.getItemCreationDate(id);
						requestedId.add(new NodeItem(nodeName, id, cd));
					}
					Collections.reverse(requestedId);
				}
			}
		} else {
			requestedId = new ArrayList<NodeItem>();
			IPubSubDAO directRepo = this.getRepository().getPubSubDAO();
			Map<String, UsersSubscription> usersSubscriptions = directRepo.getUserSubscriptions(toJid, senderJid.getBareJID());
			for (Map.Entry<String, UsersSubscription> entry : usersSubscriptions.entrySet()) {
				final UsersSubscription subscription = entry.getValue();
				final String node = entry.getKey();
				if (node.startsWith("users/"))
					continue;
				if (subscription.getSubscription() == Subscription.subscribed) {
					IItems nodeItems = this.getRepository().getNodeItems(toJid, node);
					String[] ids = nodeItems.getItemsIds();
					for (String id : ids) {
						Date cd = nodeItems.getItemCreationDate(id);
						requestedId.add(new NodeItem(node, id, cd));
					}
				}
			}
			Collections.sort(requestedId, new Comparator<NodeItem>() {

				@Override
				public int compare(NodeItem o1, NodeItem o2) {
					return o2.cacheString.compareTo(o1.cacheString);
				}
			});
		}

		final Packet iq = packet.okResult(rpubsub, 0);

		rpubsub.addChild(ritems);

		Integer maxItems = asInteger(items.getAttributeStaticStr("max_items"));
		Integer offset = 0;
		Calendar dtAfter = null;
		String afterId = null;
		String beforeId = null;

		final Element rsmGet = pubsub.getChild("set", "http://jabber.org/protocol/rsm");
		if (rsmGet != null) {
			Element m = rsmGet.getChild("max");
			if (m != null)
				maxItems = asInteger(m.getCData());
			m = rsmGet.getChild("index");
			if (m != null)
				offset = asInteger(m.getCData());
			m = rsmGet.getChild("before");
			if (m != null)
				beforeId = m.getCData();
			m = rsmGet.getChild("after");
			if (m != null)
				afterId = m.getCData();
			m = rsmGet.getChild("dt_after", "http://tigase.org/pubsub");
			if (m != null)
				dtAfter = dtf.parseDateTime(m.getCData());
		}

		final Element rsmResponse = new Element("set", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/rsm" });

		if (requestedId != null) {
			if (maxItems == null)
				maxItems = requestedId.size();

			List<Element> ritemsList = new ArrayList<Element>();

			rsmResponse.addChild(new Element("count", "" + requestedId.size()));

			String lastId = null;
			int c = 0;
			boolean allow = false;
			for (int i = 0; i < requestedId.size(); i++) {
				if (i + offset >= requestedId.size())
					continue;

				if (c >= maxItems)
					break;
				NodeItem item = requestedId.get(i + offset);
				Date cd = item.timestamp;
				if (dtAfter != null && !cd.after(dtAfter.getTime()))
					continue;

				if (afterId != null && !allow && afterId.equals(item)) {
					allow = true;
					continue;
				} else if (afterId != null && !allow)
					continue;

				if (beforeId != null && beforeId.equals(item))
					break;

				if (c == 0) {
					rsmResponse.addChild(new Element("first", item.id, new String[] { "index" }, new String[] { ""
							+ (i + offset) }));
				}

				IItems nodeItems = this.getRepository().getNodeItems(toJid, item.node);
				Element nodeItem = nodeItems.getItem(item.id);
				if (nodeName == null) {
					nodeItem.setAttribute("node", item.node);
				}

				lastId = item.id;
				ritemsList.add(nodeItem);
				++c;
			}
			if (lastId != null)
				rsmResponse.addChild(new Element("last", lastId));

			Collections.reverse(ritemsList);
			ritems.addChildren(ritemsList);

			if (maxItems != requestedId.size())
				rpubsub.addChild(rsmResponse);

		}

		packetWriter.write(iq);
	}
}