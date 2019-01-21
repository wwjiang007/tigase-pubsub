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

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.INodeMeta;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

@Bean(name = DiscoveryModule.ID, parent = PubSubComponent.class, active = true)
public class DiscoveryModule
		extends tigase.component.modules.impl.DiscoveryModule {

	private final SimpleDateFormat formatter;

	@Inject
	private IPubSubRepository repository;

	public DiscoveryModule() {
		this.formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		this.formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
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
			resultQuery.addChild(
					new Element("feature", new String[]{"var"}, new String[]{"http://jabber.org/protocol/pubsub"}));

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

			resultQuery.addChild(form.getElement());

			write(resultIq);
		}
	}

	@Override
	protected void processDiscoItems(Packet packet, JID jid, String nodeName, JID senderJID)
			throws ComponentException, RepositoryException {
		log.finest("Asking about Items of node " + nodeName);

		final JID senderJid = packet.getStanzaFrom();
		final JID toJid = packet.getStanzaTo();
		final Element element = packet.getElement();

		Element resultQuery = new Element("query", new String[]{"xmlns"},
										  new String[]{"http://jabber.org/protocol/disco#items"});

		Packet resultIq = packet.okResult(resultQuery, 0);

		AbstractNodeConfig nodeConfig = (nodeName == null)
										? null
										: repository.getNodeConfig(toJid.getBareJID(), nodeName);
		String[] nodes;

		if (nodeName != null && nodeConfig == null) {
			throw new PubSubException(Authorization.ITEM_NOT_FOUND);
		}

		if ((nodeName == null) || ((nodeConfig != null) && (nodeConfig.getNodeType() == NodeType.collection))) {
			String parentName;

			if (nodeName == null) {
				parentName = "";
				try {
					nodes = repository.getRootCollection(toJid.getBareJID());
				} catch (CachedPubSubRepository.RootCollectionSet.IllegalStateException e) {
					throw new PubSubException(Authorization.RESOURCE_CONSTRAINT);
				}
			} else {
				parentName = nodeName;
				nodes = repository.getChildNodes(toJid.getBareJID(), nodeName);
			}

			// = this.repository.getNodesList();
			if (nodes != null) {
				for (String node : nodes) {
					AbstractNodeConfig childNodeConfig = this.repository.getNodeConfig(toJid.getBareJID(), node);

					if (childNodeConfig != null) {
						boolean allowed = ((senderJid == null) || (childNodeConfig == null))
										  ? true
										  : Utils.isAllowedDomain(senderJid.getBareJID(), childNodeConfig.getDomains());
						String collection = childNodeConfig.getCollection();

						if (allowed) {
							String name = childNodeConfig.getTitle();

							name = ((name == null) || (name.length() == 0)) ? node : name;

							Element item = new Element("item", new String[]{"jid", "node", "name"},
													   new String[]{element.getAttributeStaticStr("to"), node, name});

							if (parentName.equals(collection)) {
								resultQuery.addChild(item);
							}
						} else {
							log.fine("User " + senderJid + " not allowed to see node '" + node + "'");
						}
					}
				}
			}
		} else {
			boolean allowed = ((senderJid == null) || (nodeConfig == null))
							  ? true
							  : Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains());

			if (!allowed) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}
			resultQuery.addAttribute("node", nodeName);

			IItems items = repository.getNodeItems(toJid.getBareJID(), nodeName);
			String[] itemsId = items.getItemsIds();

			if (itemsId != null) {
				for (String itemId : itemsId) {
					resultQuery.addChild(new Element("item", new String[]{"jid", "name"},
													 new String[]{element.getAttributeStaticStr("to"), itemId}));
				}
			}
		}

		write(resultIq);
	}

}
