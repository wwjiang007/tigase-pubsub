package tigase.pubsub.modules;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.JID;

@Bean(name = DiscoveryModule.ID)
public class DiscoveryModule extends tigase.component.modules.impl.DiscoveryModule {

	@Inject
	private IPubSubRepository repository;

	@Override
	protected void processDiscoInfo(final Packet packet, final JID jid, final String node, final JID senderJID)
			throws ComponentException, RepositoryException {
		if (node == null) {
			super.processDiscoInfo(packet, jid, node, senderJID);
		} else {
			final JID senderJid = packet.getStanzaFrom();

			Element resultQuery = new Element("query", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/disco#info" });

			Packet resultIq = packet.okResult(resultQuery, 0);

			AbstractNodeConfig nodeConfig = repository.getNodeConfig(packet.getStanzaTo().getBareJID(), node);

			if (nodeConfig == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}

			boolean allowed = ((senderJid == null) || (nodeConfig == null)) ? true
					: Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains());

			if (!allowed) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}
			resultQuery.addChild(new Element("identity", new String[] { "category", "type" },
					new String[] { "pubsub", nodeConfig.getNodeType().name() }));
			resultQuery.addChild(
					new Element("feature", new String[] { "var" }, new String[] { "http://jabber.org/protocol/pubsub" }));

			Form form = new Form("result", null, null);

			form.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#meta-data"));
			form.addField(Field.fieldTextSingle("pubsub#title", nodeConfig.getTitle(), "A short name for the node"));
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

		Element resultQuery = new Element("query", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/disco#items" });

		Packet resultIq = packet.okResult(resultQuery, 0);

		AbstractNodeConfig nodeConfig = (nodeName == null) ? null : repository.getNodeConfig(toJid.getBareJID(), nodeName);
		String[] nodes;

		if ((nodeName == null) || ((nodeConfig != null) && (nodeConfig.getNodeType() == NodeType.collection))) {
			String parentName;

			if (nodeName == null) {
				parentName = "";
				nodes = repository.getRootCollection(toJid.getBareJID());
			} else {
				parentName = nodeName;
				nodes = nodeConfig.getChildren();
			}

			// = this.repository.getNodesList();
			if (nodes != null) {
				for (String node : nodes) {
					AbstractNodeConfig childNodeConfig = this.repository.getNodeConfig(toJid.getBareJID(), node);

					if (childNodeConfig != null) {
						boolean allowed = ((senderJid == null) || (childNodeConfig == null)) ? true
								: Utils.isAllowedDomain(senderJid.getBareJID(), childNodeConfig.getDomains());
						String collection = childNodeConfig.getCollection();

						if (allowed) {
							String name = childNodeConfig.getTitle();

							name = ((name == null) || (name.length() == 0)) ? node : name;

							Element item = new Element("item", new String[] { "jid", "node", "name" },
									new String[] { element.getAttributeStaticStr("to"), node, name });

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
			boolean allowed = ((senderJid == null) || (nodeConfig == null)) ? true
					: Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains());

			if (!allowed) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}
			resultQuery.addAttribute("node", nodeName);

			IItems items = repository.getNodeItems(toJid.getBareJID(), nodeName);
			String[] itemsId = items.getItemsIds();

			if (itemsId != null) {
				for (String itemId : itemsId) {
					resultQuery.addChild(new Element("item", new String[] { "jid", "name" },
							new String[] { element.getAttributeStaticStr("to"), itemId }));
				}
			}
		}

		write(resultIq);
	}

}
