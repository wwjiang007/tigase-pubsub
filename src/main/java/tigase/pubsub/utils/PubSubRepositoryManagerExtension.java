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
package tigase.pubsub.utils;

import tigase.component.exceptions.RepositoryException;
import tigase.db.util.importexport.AbstractImporterExtension;
import tigase.db.util.importexport.Exporter;
import tigase.db.util.importexport.ImporterExtension;
import tigase.db.util.importexport.RepositoryManagerExtensionBase;
import tigase.pubsub.*;
import tigase.pubsub.modules.mam.PubSubQuery;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.INodeMeta;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Message;
import tigase.util.ui.console.CommandlineParameter;
import tigase.xml.Element;
import tigase.xml.XMLUtils;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMItemHandler;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;
import tigase.xmpp.mam.util.MAMRepositoryManagerExtensionHelper;

import java.io.Writer;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static tigase.db.util.importexport.Exporter.EXPORT_MAM_SINCE;
import static tigase.db.util.importexport.RepositoryManager.isSet;

public class PubSubRepositoryManagerExtension extends RepositoryManagerExtensionBase {

	private static final Logger log = Logger.getLogger(PubSubRepositoryManagerExtension.class.getSimpleName());

	private final CommandlineParameter INCLUDE_PUBSUB = new CommandlineParameter.Builder(null, "include-pubsub").type(Boolean.class)
			.description("Include PubSub component data")
			.defaultValue("false")
			.requireArguments(false)
			.build();

	private final CommandlineParameter EXCLUDE_PEP = new CommandlineParameter.Builder(null, "exclude-pep").type(Boolean.class)
			.description("Exclude user PEP data")
			.defaultValue("false")
			.requireArguments(false)
			.build();

	@Override
	public Stream<CommandlineParameter> getImportParameters() {
		return Stream.concat(super.getImportParameters(), Stream.of(INCLUDE_PUBSUB));
	}

	@Override
	public Stream<CommandlineParameter> getExportParameters() {
		return Stream.concat(super.getExportParameters(), Stream.of(INCLUDE_PUBSUB, EXCLUDE_PEP, EXPORT_MAM_SINCE));
	}

	@Override
	public void exportDomainData(String domain, Writer writer) throws Exception {
		if (!isSet(INCLUDE_PUBSUB)) {
			return;
		}
		List<String> names = getNamesOfComponent(PubSubComponent.class);
		log.finest("for domain " + domain + " found following PubSub components: " + names);
		for (String name : names) {
			BareJID serviceJID = BareJID.bareJIDInstance(name + "." + domain);
			var pubSubDAO = getRepository(PubSubDAO.class, serviceJID.getDomain());
			if (pubSubDAO.getNodesCount(serviceJID) > 0) {
				log.info("exporting PubSub data for component domain " + name + "." + domain + "..");
				exportInclude(writer, getRootPath().resolve(name + "." + domain + ".xml"), pubsubWriter -> {
					pubsubWriter.append("<pubsub xmlns=\"tigase:xep-0227:pubsub:0\" name=\"")
							.append(name)
							.append("\">\n");
					exportData(serviceJID, false, pubsubWriter);
					pubsubWriter.append("\n</pubsub>");
				});
			}
		}
	}

	@Override
	public void exportUserData(Path userDirPath, BareJID serviceJid, Writer writer)
			throws Exception {
		if (!isSet(EXCLUDE_PEP)) {
			exportInclude(writer, userDirPath.resolve("pep.xml"), pepWriter -> {
				exportData(serviceJid, true, pepWriter);
			});
		}
	}

	public void exportData(BareJID serviceJid, boolean isPEP, Writer writer) throws Exception {
		PubSubDAO pubSubDAO = getRepository(PubSubDAO.class, serviceJid.getDomain());
		String[] nodeNames = pubSubDAO.getChildNodes(serviceJid, null);
		log.finest("for JID " + serviceJid + " found PEP nodes: " + Arrays.asList(nodeNames));
		writer.write("<pubsub xmlns='http://jabber.org/protocol/pubsub#owner'>");
		for (String nodeName : nodeNames) {
			INodeMeta nodeMeta = pubSubDAO.getNodeMeta(serviceJid, nodeName);
			if (nodeMeta != null) {
				writer.append("<configure node=\"").append(XMLUtils.escape(nodeName)).append("\"");
				if (!isPEP) {
					writer.append(" xmlns:tigase=\"tigase:xep-0227:pubsub:0\"").append(" tigase:createdBy=\"").append(XMLUtils.escape(nodeMeta.getCreator().toString())).append("\"");
				}
				writer.append(">");
				writer.write(nodeMeta.getNodeConfig().getFormElement().toString());
				writer.write("</configure>");
				Map<BareJID, UsersAffiliation> affs = pubSubDAO.getNodeAffiliations(serviceJid, nodeMeta.getNodeId());
				if (affs != null && !affs.isEmpty()) {
					writer.append("<affiliations node=\"").append(XMLUtils.escape(nodeName)).append("\">");
					for (UsersAffiliation aff : affs.values()) {
						writer.append("<affiliation jid=\"").append(XMLUtils.escape(aff.getJid().toString())).append("\" affiliation=\"").append(aff.getAffiliation().name()).append("\"/>");
					}
					writer.write("</affiliations>");
				}
				Map<BareJID, UsersSubscription> subs = pubSubDAO.getNodeSubscriptions(serviceJid, nodeMeta.getNodeId());
				if (subs != null && !subs.isEmpty()) {
					writer.append("<subscriptions node=\"").append(XMLUtils.escape(nodeName)).append("\">");
					for (UsersSubscription sub : subs.values()) {
						writer.append("<subscription jid=\"").append(XMLUtils.escape(sub.getJid().toString())).append("\" subscription=\"").append(sub.getSubscription().name()).append("\"");
						if (sub.getSubid() != null) {
							writer.append(" subid=\"").append(sub.getSubid()).append("\"");
						}
						writer.append("/>");
					}
					writer.write("</subscriptions>");
				}
			} else {
				throw new RuntimeException("Couldn't load metadata info for JID = " + serviceJid + ", node = " + nodeName);
			}
		}
		writer.write("</pubsub>");
		writer.write("<pubsub xmlns='http://jabber.org/protocol/pubsub'>");
		for (String nodeName : nodeNames) {
			INodeMeta nodeMeta = pubSubDAO.getNodeMeta(serviceJid, nodeName);
			if (nodeMeta != null) {
				writer.append("<items node=\"").append(XMLUtils.escape(nodeName)).append("\">");
				String[] itemIds = pubSubDAO.getItemsIds(serviceJid, nodeMeta.getNodeId(), CollectionItemsOrdering.byUpdateDate);
				if (itemIds != null && itemIds.length > 0) {
					for (String itemId : itemIds) {
						IItems.IItem item = pubSubDAO.getItem(serviceJid, nodeMeta.getNodeId(), itemId);
						if (item != null && item.getItem() != null) {
							Element itemEl = item.getItem().clone();
							if (item.getUUID() != null || !isPEP) {
								itemEl.addAttribute("xmlns:tigase", "tigase:xep-0227:pubsub:0");
								if (item.getUUID() != null) {
									itemEl.addAttribute("tigase:stableId", item.getUUID().toString());
								}
							}
							writer.write(itemEl.toString());
						}
					}
				}
				writer.write("</items>");
				exportMAMDataFromRepository(pubSubDAO, serviceJid, nodeName, nodeMeta, serviceJid, writer);
			}
		}
		writer.write("</pubsub>");
	}

	public static void exportMAMDataFromRepository(IPubSubDAO mamRepository, BareJID repoJID, String nodeName, INodeMeta nodeMeta, BareJID askingJID, Writer writer) throws Exception {
		writer.append("<archive xmlns='urn:xmpp:pie:0#mam' node=\"").append(XMLUtils.escape(nodeName)).append("\">");
		PubSubQuery query = mamRepository.newQuery(repoJID);
		query.setComponentJID(JID.jidInstance(repoJID));
		query.setQuestionerJID(JID.jidInstance(askingJID));
		query.setPubsubNode(nodeName);
		query.setXMLNS("urn:xmpp:mam:2");
		Exporter.getExportMAMSinceValue().ifPresent(query::setStart);
		query.getRsm().setMax(Integer.MAX_VALUE);
		mamRepository.queryItems(query, nodeMeta.getNodeId(), new MAMItemHandler() {
			@Override
			public void itemFound(Query query, MAMRepository.Item item) {
				Element result = this.prepareResult(query, item);

				if (result != null) {
					try {
						writer.append(result.toString());
					} catch (Throwable ex) {
						log.log(Level.SEVERE, ex.getMessage(), ex);
					}
				}
			}
		});
		writer.append("</archive>");
	}

	@Override
	public ImporterExtension startImportDomainData(String domain, String name,
												   Map<String, String> attrs) throws Exception {
		if (!"pubsub".equals(name)) {
			return null;
		}
		if (!"tigase:xep-0227:pubsub:0".equals(attrs.get("xmlns"))) {
			return null;
		}

		String prefix = attrs.get("name");
		String componentDomain = prefix != null ? (prefix + "." + domain) : domain;
		return new PubSubComponentImporterExtension(getRepository(PubSubDAO.class, componentDomain), componentDomain, isSet(INCLUDE_PUBSUB));
	}

	@Override
	public ImporterExtension startImportUserData(BareJID userJid, String name,
												 Map<String, String> attrs) throws Exception {
		if (!"pubsub".equals(name)) {
			return null;
		}
		return switch (attrs.get("xmlns")) {
			case "http://jabber.org/protocol/pubsub#owner" -> new PubSubOwnerImporterExtension(getRepository(PubSubDAO.class, userJid.getDomain()), userJid, true);
			case "http://jabber.org/protocol/pubsub" -> new PubSubDataImporterExtension(getRepository(PubSubDAO.class, userJid.getDomain()), userJid, true);
			default -> null;
		};
	}

	public static class PubSubComponentImporterExtension
			extends AbstractImporterExtension {

		private final PubSubDAO pubSubDAO;
		private ImporterExtension activeExtension = null;
		private final String domain;
		private final boolean includePubSub;
		private int depth = 0;

		public PubSubComponentImporterExtension(PubSubDAO pubSubDAO, String domain, boolean includePubSub) {
			this.domain = domain;
			this.pubSubDAO = pubSubDAO;
			this.includePubSub = includePubSub;
			if (includePubSub) {
				log.info("importing PubSub data for component domain " + domain + "...");
			}
		}

		@Override
		public boolean startElement(String name, Map<String, String> attrs) throws Exception {
			if (!includePubSub) {
				depth++;
				return true;
			}
			if (activeExtension != null && activeExtension.startElement(name, attrs)) {
				return true;
			}
			if (!"pubsub".equals(name)) {
				return false;
			}
			activeExtension = switch (attrs.get("xmlns")) {
				case "http://jabber.org/protocol/pubsub#owner" -> new PubSubOwnerImporterExtension(pubSubDAO, BareJID.bareJIDInstanceNS(domain), false);
				case "http://jabber.org/protocol/pubsub" -> new PubSubDataImporterExtension(pubSubDAO, BareJID.bareJIDInstanceNS(domain), false);
				default -> null;
			};
			return activeExtension != null;
		}

		@Override
		public boolean handleElement(Element element) throws Exception {
			if (activeExtension != null && activeExtension.handleElement(element)) {
				return true;
			}
			return false;
		}

		@Override
		public boolean endElement(String name) throws Exception {
			if (!includePubSub) {
				boolean inside = depth > 0;
				depth--;
				return inside;
			}
			if (activeExtension != null) {
				if (activeExtension.endElement(name)) {
					return true;
				}
				if ("pubsub".equals(name)) {
					activeExtension.close();
					activeExtension = null;
					return true;
				}
			}
			return false;
		}
	}

	public static class PubSubOwnerImporterExtension extends AbstractImporterExtension {

		enum State {
			root,
			affiliations,
			subscriptions
		}

		private final PubSubDAO pubSubDAO;
		private final BareJID serviceJID;
		private final boolean isPEP;

		private State state = State.root;
		private String nodeName;
		private INodeMeta nodeMeta;

		public PubSubOwnerImporterExtension(PubSubDAO pubSubDAO, BareJID serviceJID, boolean isPEP) {
			this.pubSubDAO = pubSubDAO;
			this.serviceJID = serviceJID;
			this.isPEP = isPEP;
		}

		@Override
		public boolean startElement(String name, Map<String, String> attrs) throws Exception {
			return  switch (state) {
				case root -> switch (name) {
					case "affiliations" -> {
						nodeName = Optional.ofNullable(attrs.get("node")).orElseThrow();
						nodeMeta = pubSubDAO.getNodeMeta(serviceJID, nodeName);
						state = State.affiliations;
						yield true;
					}
					case "subscriptions" -> {
						nodeName = Optional.ofNullable(attrs.get("node")).orElseThrow();
						nodeMeta = pubSubDAO.getNodeMeta(serviceJID, nodeName);
						state = State.subscriptions;
						yield true;
					}
					default -> false;
				};
				default -> false;
			};
		}

		@Override
		public boolean handleElement(Element element) throws Exception {
			return switch (state) {
				case root -> switch (element.getName()) {
					case "configure" -> {
						String nodeName = XMLUtils.unescape(element.getAttributeStaticStr("node"));
						Element config = element.getChild("x", "jabber:x:data");
						INodeMeta nodeMeta = pubSubDAO.getNodeMeta(serviceJID, nodeName);
						if (nodeMeta != null) {
							pubSubDAO.updateNodeConfig(serviceJID, nodeMeta.getNodeId(), config.toString(), null);
						} else {
							BareJID creatorJID = isPEP
												 ? serviceJID
												 : Optional.ofNullable(
																 element.getAttributeStaticStr("tigase:createdBy"))
														 .map(XMLUtils::unescape)
														 .map(BareJID::bareJIDInstanceNS)
														 .orElse(serviceJID);
							LeafNodeConfig leafNodeConfig = new LeafNodeConfig(nodeName);
							leafNodeConfig.getForm().copyValuesFrom(config);
							pubSubDAO.createNode(serviceJID, nodeName, creatorJID, leafNodeConfig, NodeType.leaf, null, true);
						}
						yield true;
					}
					default -> false;
				};
				case affiliations -> switch (element.getName()) {
					case "affiliation" -> {
						BareJID jid = Optional.ofNullable(element.getAttributeStaticStr("jid"))
								.map(BareJID::bareJIDInstanceNS)
								.orElseThrow();
						Affiliation affiliation = Optional.ofNullable(element.getAttributeStaticStr("affiliation")).map(Affiliation::valueOf).orElse(Affiliation.none);
						if (affiliation != Affiliation.none) {
							pubSubDAO.updateNodeAffiliation(serviceJID, nodeMeta.getNodeId(), nodeName,
															new UsersAffiliation(jid, affiliation));
						}
						yield true;
					}
					default -> false;
				};
				case subscriptions ->  switch (element.getName()) {
					case "subscription" -> {
						BareJID jid = Optional.ofNullable(element.getAttributeStaticStr("jid"))
								.map(BareJID::bareJIDInstanceNS)
								.orElseThrow();
						String subid = element.getAttributeStaticStr("subid");
						Subscription subscriptionType = Optional.ofNullable(
										element.getAttributeStaticStr("subscription"))
								.map(Subscription::valueOf)
								.orElse(Subscription.none);
						if (subscriptionType != Subscription.none) {
							UsersSubscription subscription = new UsersSubscription(jid, subid, subscriptionType);
							pubSubDAO.updateNodeSubscription(serviceJID, nodeMeta.getNodeId(), nodeName, subscription);
						}
						yield true;
					}
					default -> false;
				};
				default -> false;
			};
		}

		@Override
		public boolean endElement(String name) throws Exception {
			return switch (state) {
				case affiliations -> {
					if ("affiliations".equals(name)) {
						nodeMeta = null;
						nodeName = null;
						state = State.root;
						yield true;
					}
					yield false;
				}
				case subscriptions -> {
					if ("subscriptions".equals(name)) {
						nodeMeta = null;
						nodeName = null;
						state = State.root;
						yield true;
					}
					yield false;
				}
				default -> false;
			};
		}
	}
	
	public static class PubSubDataImporterExtension
			extends AbstractImporterExtension {

		private final boolean isPEP;

		enum State {
			root,
			items,
			archive
		}

		private final PubSubDAO pubSubDAO;
		private final BareJID serviceJID;

		private String nodeName;
		private INodeMeta nodeMeta;
		private State state = State.root;

		private ImporterExtension activeExtension;
		private Class<? extends PubSubMAMImporterExtension> mamImporterExtension;
		
		public PubSubDataImporterExtension(PubSubDAO pubSubDAO, BareJID serviceJID, boolean isPEP) {
			this(pubSubDAO, serviceJID, isPEP, PubSubMAMImporterExtension.class);
		}

		public PubSubDataImporterExtension(PubSubDAO pubSubDAO, BareJID serviceJID, boolean isPEP, Class<? extends PubSubMAMImporterExtension> mamImporterExtension) {
			this.pubSubDAO = pubSubDAO;
			this.serviceJID = serviceJID;
			this.isPEP = isPEP;
			this.mamImporterExtension = mamImporterExtension;
		}

		@Override
		public boolean startElement(String name, Map<String, String> attrs) throws Exception {
			return switch (state) {
				case root -> switch (name) {
					case "items" -> {
						nodeName = attrs.get("node");
						nodeMeta = pubSubDAO.getNodeMeta(serviceJID, nodeName);
						state = State.items;
						yield true;
					}
					case "archive" -> {
						if ("urn:xmpp:pie:0#mam".equals(attrs.get("xmlns"))) {
							state = State.archive;
							nodeName = attrs.get("node");
							activeExtension = mamImporterExtension.getConstructor(PubSubDAO.class, BareJID.class, String.class).newInstance(pubSubDAO, serviceJID, nodeName);
							log.finest("starting " + activeExtension.getClass().getSimpleName() + "...");
							yield true;
						}
						yield false;
					}
					default -> false;
				};
				default -> false;
			};
		}

		@Override
		public boolean handleElement(Element element) throws Exception {
			if (activeExtension != null && activeExtension.handleElement(element)) {
				return true;
			}
			return switch (state) {
				case items -> {
					if ("item".equals(element.getName())) {
						String id = element.getAttributeStaticStr("id");
						// simplification as this field is not used anywhere after being inserted
						String publisher = isPEP ? serviceJID.toString() : serviceJID.toString();
						String stableId = element.getAttributeStaticStr("tigase:stableId");
						List<String> attrsToRemove = element.getAttributes().keySet().stream().filter(key -> key.contains("tigase")).toList();
						for (String key : attrsToRemove) {
							element.removeAttribute(key);
						}
						log.finest("inserting for " + serviceJID + " node = " + nodeName + ", item " + id);
						pubSubDAO.writeItem(serviceJID, nodeMeta.getNodeId(), System.currentTimeMillis(), id, publisher, element, stableId);
						yield true;
					}
					yield false;
				}
				default -> false;
			};
		}

		@Override
		public boolean endElement(String name) throws Exception {
			return switch (state) {
				case items -> switch (name) {
					case "items" -> {
						nodeName = null;
						nodeMeta = null;
						state = State.root;
						yield true;
					}
					default -> false;
				};
				case archive -> switch (name) {
					case "archive" -> {
						nodeName = null;
						activeExtension.close();;
						activeExtension = null;
						state = State.root;
						yield true;
					}
					default -> false;
				};
				default -> false;
			};
		}
	}

	public static class PubSubMAMImporterExtension extends MAMRepositoryManagerExtensionHelper.AbstractImporterExtension {

		protected final INodeMeta nodeMeta;
		protected final String nodeName;
		protected final PubSubDAO pubSubDAO;
		protected final BareJID serviceJID;

		public PubSubMAMImporterExtension(PubSubDAO pubSubDAO, BareJID serviceJID, String nodeName)
				throws RepositoryException {
			this.pubSubDAO = pubSubDAO;
			this.serviceJID = serviceJID;
			this.nodeName = nodeName;
			this.nodeMeta = pubSubDAO.getNodeMeta(serviceJID, nodeName);
		}

		@Override
		protected boolean handleMessage(Message message, String stableId, Date timestamp, Element source)
				throws Exception {
			Element event = message.getElemChild("event","http://jabber.org/protocol/pubsub#event");
			if (event == null) {
				return false;
			}
			Element items = event.getChild("items");
			if (items == null) {
				return false;
			}
			Element item = items.getChild("item");
			if (item == null) {
				item = items.getChild("retract");
			}
			String itemId = item == null ? null : item.getAttributeStaticStr("id");
			if (itemId == null) {
				return false;
			}
			if (pubSubDAO.getItem(serviceJID, nodeMeta.getNodeId(), itemId) == null) {
				pubSubDAO.addMAMItem(serviceJID, nodeMeta.getNodeId(), stableId, message.getElement(), timestamp, itemId);
			} else {
				log.finest("MAM entry for item with id = " + itemId + ", already existed..");
			}
			return true;
		}
	}

}
