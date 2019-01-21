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
package tigase.pubsub.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.mam.Query;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Bean(name = "retrieveItemsModule", parent = PubSubComponent.class, active = true)
public class RetrieveItemsModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"))
			.add(ElementCriteria.name("items"));

	private final TimestampHelper timestampHelper = new TimestampHelper();

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#retrieve-items"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(final Packet packet) throws PubSubException {
		try {
			final BareJID toJid = packet.getStanzaTo().getBareJID();
			final Element pubsub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element items = pubsub.getChild("items");
			final String nodeName = items.getAttributeStaticStr("node");
			final JID senderJid = packet.getStanzaFrom();

			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
			}

			// XXX CHECK RIGHTS AUTH ETC
			logic.checkAccessPermission(toJid, nodeName, senderJid);

			final Element rpubsub = new Element("pubsub", new String[]{"xmlns"},
												new String[]{"http://jabber.org/protocol/pubsub"});

			List<String> requestedId = extractItemsIds(items);
			if (requestedId != null) {
				final Element ritems = new Element("items", new String[]{"node"}, new String[]{nodeName});
				IItems nodeItems = getRepository().getNodeItems(toJid, nodeName);
				for (String id : requestedId) {
					Element payload = nodeItems.getItem(id);
					if (payload != null) {
						ritems.addChild(payload);
					}
				}
				rpubsub.addChild(ritems);
			} else {
				Query query = getRepository().newQuery();
				query.setComponentJID(packet.getStanzaTo());
				query.setQuestionerJID(senderJid);
				query.setPubsubNode(nodeName);

				final Integer maxItems = asInteger(items.getAttributeStaticStr("max_items"));
				final Element rsmGet = pubsub.getChild("set", "http://jabber.org/protocol/rsm");
				if (rsmGet != null) {
					query.getRsm().fromElement(rsmGet);
					Element m = rsmGet.getChild("dt_after", "http://tigase.org/pubsub");
					if (m != null) {
						query.setStart(timestampHelper.parseTimestamp(m.getCData()));
					}
					m = rsmGet.getChild("dt_before", "http://tigase.org/pubsub");
					if (m != null) {
						query.setEnd(timestampHelper.parseTimestamp(m.getCData()));
					}
				} else {
					if (maxItems != null) {
						query.getRsm().setHasBefore(true);
						query.getRsm().setMax(maxItems);
					}
				}

				List<IPubSubRepository.Item> queryResults = new ArrayList<>();
				getRepository().queryItems(query, (query1, item) -> {
					queryResults.add(item);
					if (query.getRsm().getFirst() == null) {
						query.getRsm().setFirst(item.getId());
					}
					query.getRsm().setLast(item.getId());
				});

				queryResults.stream()
						.collect(Collectors.groupingBy(item -> item.getNode()))
						.forEach((rnodeName, rnodeItems) -> {
							final Element ritems = new Element("items", new String[]{"node"}, new String[]{rnodeName});
							for (IPubSubRepository.Item ritem : rnodeItems) {
								ritems.addChild(ritem.getMessage());
							}
							rpubsub.addChild(ritems);
						});

				if (query.getRsm().getCount() > 0) {
					if (maxItems == null) {
						rpubsub.addChild(query.getRsm().toElement());
					}
				} else {
					rpubsub.addChild(new Element("items", new String[]{"node"}, new String[]{nodeName}));
				}
			}

			final Packet iq = packet.okResult(rpubsub, 0);
			iq.setXMLNS(Packet.CLIENT_XMLNS);
			packetWriter.write(iq);

		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing retrieve items packet", e);

			throw new RuntimeException(e);
		}
	}

	private Integer asInteger(String attribute) {
		if (attribute == null) {
			return null;
		}

		return Integer.parseInt(attribute);
	}

	private List<String> extractItemsIds(final Element items) throws PubSubException {
		List<Element> il = items.getChildren();

		if ((il == null) || (il.size() == 0)) {
			return null;
		}

		final List<String> result = new ArrayList<String>();

		for (Element i : il) {
			final String id = i.getAttributeStaticStr("id");

			if (!"item".equals(i.getName()) || (id == null)) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
			result.add(id);
		}

		return result;
	}
}