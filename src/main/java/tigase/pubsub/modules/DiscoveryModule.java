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

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.*;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.INodeMeta;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.rsm.RSM;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Bean(name = DiscoveryModule.ID, parent = PubSubComponent.class, active = true)
public class DiscoveryModule
		extends tigase.component.modules.impl.DiscoveryModule {

	public static final String PUBSUB_FEATURE_METADATA = "http://jabber.org/protocol/pubsub#meta-data";

	private final SimpleDateFormat formatter;
	private final String[] features;

	@Inject
	private IPubSubConfig config;
	@Inject
	protected PubSubLogic pubSubLogic;
	@Inject
	private IPubSubRepository repository;
	@Inject(nullAllowed = true)
	private Predicate<Packet> packetFilter;

	public DiscoveryModule() {
		this.formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		this.formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		this.features = Stream.concat(Arrays.stream(super.getFeatures()), Stream.of(PUBSUB_FEATURE_METADATA))
				.toArray(String[]::new);
	}

	@Override
	public String[] getFeatures() {
		return features;
	}

	@Override
	public boolean canHandle(Packet packet) {
		if (packetFilter != null) {
			if (!packetFilter.test(packet)) {
				return false;
			}
		}
		Criteria criteria = getModuleCriteria();
		return criteria != null && criteria.match(packet.getElement());
	}
	
	@Override
	protected void processDiscoInfo(final Packet packet, final JID jid, final String node, final JID senderJID)
			throws ComponentException, RepositoryException {
		if (node == null) {
			super.processDiscoInfo(packet, jid, node, senderJID);
		} else {
			final JID senderJid = packet.getStanzaFrom();

			Element resultQuery = new Element("query", new String[]{"xmlns"},
											  new String[]{"http://jabber.org/protocol/disco#info"});

			Packet resultIq = packet.okResult(resultQuery, 0);

			INodeMeta nodeMeta = repository.getNodeMeta(packet.getStanzaTo().getBareJID(), node);
			if (nodeMeta == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}

			AbstractNodeConfig nodeConfigClone;
			try {
				nodeConfigClone = nodeMeta.getNodeConfig().clone();
			} catch (CloneNotSupportedException ex) {
				throw new RepositoryException("Exception retrieving node configuration", ex);
			}

			boolean allowed = ((senderJid == null) || (nodeConfigClone == null))
							  ? true
							  : Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfigClone.getDomains());

			if (!allowed) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}
			resultQuery.addChild(new Element("identity", new String[]{"category", "type"},
											 new String[]{"pubsub", nodeConfigClone.getNodeType().name()}));
			switch (nodeConfigClone.getNodeType()) {
				case leaf:
					resultQuery.addChild(new Element("identity", new String[]{"category", "type"},
							new String[]{"hierarchy", "leaf"}));
					break;
				case collection:
					resultQuery.addChild(new Element("identity", new String[]{"category", "type"},
							new String[]{"hierarchy", "branch"}));
					break;
				default:
					// this should not happen..
					break;
			}
			resultQuery.addChild(
					new Element("feature", new String[]{"var"}, new String[]{"http://jabber.org/protocol/pubsub"}));
			resultQuery.addChild(new Element("feature", new String[]{"var"},
											 new String[]{"http://jabber.org/protocol/pubsub#config-node-max"}));

			Form form = nodeConfigClone.getForm();

			form.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#meta-data"));

			List<String> owners = new ArrayList<>();
			List<String> publishers = new ArrayList<>();

			IAffiliations affiliations = repository.getNodeAffiliations(packet.getStanzaTo().getBareJID(), node);
			for (UsersAffiliation affiliation : affiliations.getAffiliations()) {
				if (affiliation.getAffiliation() == null) {
					continue;
				}

				switch (affiliation.getAffiliation()) {
					case owner:
						owners.add(affiliation.getJid().toString());
						break;
					case publisher:
						publishers.add(affiliation.getJid().toString());
						break;
					default:
						break;
				}
			}
			form.addField(
					Field.fieldJidMulti("pubsub#owner", owners.toArray(new String[owners.size()]), "Node owners"));
			form.addField(Field.fieldJidMulti("pubsub#publisher", publishers.toArray(new String[publishers.size()]),
											  "Publishers to this node"));

			BareJID creator = nodeMeta.getCreator();
			String creationDateStr = "";
			if (nodeMeta.getCreationTime() != null) {
				synchronized (formatter) {
					creationDateStr = formatter.format(nodeMeta.getCreationTime());
				}
			}
			form.addField(
					Field.fieldJidSingle("pubsub#creator", creator != null ? creator.toString() : "", "Node creator"));
			form.addField(Field.fieldTextSingle("pubsub#creation_date", creationDateStr, "Creation date"));

			form.addField(Field.fieldTextSingle("pubsub#num_subscribers", String.valueOf(
					repository.getNodeSubscriptions(packet.getStanzaTo().getBareJID(), node).size()),
												"Number of subscribers to this node"));

			resultQuery.addChild(form.getElement());

			write(resultIq);
		}
	}

	private static final String[] EMPTY_NODES = new String[0];

	@Override
	protected List<Element> prepareDiscoItems(JID toJid, String nodeName, JID senderJid, RSM rsm)
			throws ComponentException, RepositoryException {
		log.finest("Asking about Items of node " + nodeName);
		
		AbstractNodeConfig nodeConfig = (nodeName == null)
										? null
										: repository.getNodeConfig(toJid.getBareJID(), nodeName);
		String[] nodes;

		if (nodeName != null && nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND);
		}
		
		if ((nodeName == null) || ((nodeConfig != null) && (nodeConfig.getNodeType() == NodeType.collection))) {

			if (nodeName == null) {
				try {
					nodes = repository.getRootCollection(toJid.getBareJID());
				} catch (CachedPubSubRepository.RootCollectionSet.IllegalStateException e) {
					throw new PubSubException(Authorization.RESOURCE_CONSTRAINT);
				}
			} else {
				nodes = repository.getChildNodes(toJid.getBareJID(), nodeName);
			}

			// = this.repository.getNodesList();
			if (nodes != null) {
				int count = nodes.length;
				int index = 0;
				boolean inverted = false;
				if (nodes.length > 0 && rsm != null) {
					String[] originalNodes = nodes;
					nodes = prefilterNodesWithRSM(nodes, rsm);
					if (nodes.length > 0 && originalNodes.length > nodes.length) {
						for (int i=0; i<originalNodes.length; i++) {
							if (originalNodes[i].equals(nodes[0])) {
								index = i;
								break;
							}
						}
					}
					inverted = rsm.hasBefore();
					if (inverted) {
						List<String> tmp = Arrays.asList(nodes);
						Collections.reverse(tmp);
						nodes = tmp.toArray(new String[tmp.size()]);
					}
				}

				List<Element> results = new ArrayList<>();
				for (String node : nodes) {
					AbstractNodeConfig childNodeConfig = this.repository.getNodeConfig(toJid.getBareJID(), node);

					if (childNodeConfig != null) {
						boolean allowed = ((senderJid == null) || (childNodeConfig == null))
										  ? true
										  : isNodeDiscoverable(toJid.getBareJID(), childNodeConfig, senderJid);

						if (allowed) {
							String name = childNodeConfig.getTitle();

							name = ((name == null) || (name.length() == 0)) ? node : name;

							Element item = new Element("item", new String[]{"jid", "node", "name"},
													   new String[]{toJid.toString(), node, name});

							results.add(item);
							if (rsm != null && results.size() >= rsm.getMax()) {
								break;
							}
						} else {
							log.fine("User " + senderJid + " not allowed to see node '" + node + "'");
						}
					}
				}

				if (inverted) {
					index = nodes.length - results.size();
					Collections.reverse(results);
				}
				if (rsm != null && !results.isEmpty()) {
					rsm.setResults(count, results.get(0).getAttributeStaticStr("node"),
								   results.get(results.size() - 1).getAttributeStaticStr("node"));
					rsm.setIndex(index);
				}
				return results;
			} else {
				return Collections.emptyList();
			}
		} else {
			pubSubLogic.checkPermission(toJid.getBareJID(), nodeName, senderJid, PubSubLogic.Action.retrieveItems);
			
			boolean allowed = ((senderJid == null) || (nodeConfig == null))
							  ? true
							  : Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains());

			if (!allowed) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			IItems items = repository.getNodeItems(toJid.getBareJID(), nodeName);
			String[] itemsId = items.getItemsIds(nodeConfig.getCollectionItemsOrdering());

			if (itemsId != null) {
				List<Element> results = new ArrayList<>();
				for (String itemId : itemsId) {
					results.add(new Element("item", new String[]{"jid", "name"},
													 new String[]{toJid.toString(), itemId}));
				}
				return results;
			}
			
			return Collections.emptyList();
		}
	}

	protected boolean isNodeDiscoverable(BareJID serviceJid, AbstractNodeConfig nodeConfig, JID senderJid) throws ComponentException, RepositoryException {
		return Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains());
	}
	
	protected String[] prefilterNodesWithRSM(String[] nodes, RSM rsm) throws PubSubException {
		Integer start = null;
		if (rsm.getAfter() != null) {
			for (int i = 0; i < nodes.length; i++) {
				if (nodes[i].equals(rsm.getAfter())) {
					start = i + 1;
					break;
				}
			}
			if (start == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}
		} else if (rsm.getIndex() != null) {
			start = rsm.getIndex();
		}

		Integer stop = null;
		if (rsm.getBefore() != null) {
			for (int i = nodes.length-1; i >= 0; i--) {
				if (nodes[i].equals(rsm.getBefore())) {
					stop = i;
					break;
				}
			}
			if (stop == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}
		}

		if (start == null) {
			start = 0;
		}
		if (stop == null) {
			stop = nodes.length;
		}
		if (start <= stop) {
			return Arrays.copyOfRange(nodes, start, stop);
		} else {
			return EMPTY_NODES;
		}
	}

	@Override
	protected Packet prepareDiscoInfoResponse(Packet packet, JID jid, String node, JID senderJID) {
		Packet result =  super.prepareDiscoInfoResponse(packet, jid, node, senderJID);
		if (node == null && jid.getLocalpart() != null && config.isPepPeristent()) {
			Element query = result.getElement().getChild("query", "http://jabber.org/protocol/disco#info");
			if (query != null) {
				Stream.of("http://jabber.org/protocol/pubsub#auto-create",
						  "http://jabber.org/protocol/pubsub#auto-subscribe")
						.map(feature -> new Element("feature", new String[]{"var"}, new String[]{feature}))
						.forEach(query::addChild);
			}
		}
		return result;
	}

	protected IPubSubRepository getRepository() {
		return repository;
	}
}
