package tigase.pubsub.modules.ext.presence;

import java.util.Collection;
import java.util.logging.Level;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.disteventbus.EventBus;
import tigase.disteventbus.EventHandler;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

@Bean(name = "presenceNotifierModule")
public class PresenceNotifierModule extends AbstractPubSubModule implements Initializable {

	@Inject
	private EventBus eventBus;

	private final EventHandler loginToNodeHandler;

	private final EventHandler logoffFromNodeHandler;

	@Inject
	private PresencePerNodeExtension presencePerNodeExtension;

	@Inject
	private PublishItemModule publishItemModule;

	private final EventHandler updatePresenceHandler;

	public PresenceNotifierModule() {

		this.loginToNodeHandler = new EventHandler() {

			@Override
			public void onEvent(String name, String xmlns, Element event) {
				try {
					Packet presence = Packet.packetInstance(event.getChild("presence"));
					JID occupantJID = presence.getStanzaFrom();
					String node = event.getCData(new String[] { "LoginToNode", "node" });
					BareJID serviceJID = BareJID.bareJIDInstanceNS(event.getCData(new String[] { "LoginToNode", "service" }));

					PresenceNotifierModule.this.onLoginToNode(serviceJID, node, occupantJID, presence);
				} catch (Exception e) {
					log.throwing(PresenceNotifierModule.class.getName(), "onLoginToNode", e);
				}
			}
		};

		this.logoffFromNodeHandler = new EventHandler() {

			@Override
			public void onEvent(String name, String xmlns, Element event) {
				try {
					Packet presence = Packet.packetInstance(event.getChild("presence"));
					String node = event.getCData(new String[] { "LogoffFromNode", "node" });
					BareJID serviceJID = BareJID.bareJIDInstanceNS(
							event.getCData(new String[] { "LogoffFromNode", "service" }));
					JID occupantJID = JID.jidInstanceNS(event.getCData(new String[] { "LogoffFromNode", "sender" }));

					PresenceNotifierModule.this.onLogoffFromNode(serviceJID, node, occupantJID, presence);
				} catch (Exception e) {
					log.throwing(PresenceNotifierModule.class.getName(), "onLogoffFromNode", e);
				}
			}
		};

		this.updatePresenceHandler = new EventHandler() {

			@Override
			public void onEvent(String name, String xmlns, Element event) {
				try {
					Packet presence = Packet.packetInstance(event.getChild("presence"));
					JID occupantJID = presence.getStanzaFrom();
					String node = event.getCData(new String[] { "UpdatePresence", "node" });
					BareJID serviceJID = BareJID.bareJIDInstanceNS(
							event.getCData(new String[] { "UpdatePresence", "service" }));

					PresenceNotifierModule.this.onPresenceUpdate(serviceJID, node, occupantJID, presence);
				} catch (Exception e) {
					log.throwing(PresenceNotifierModule.class.getName(), "onPresenceUpdate", e);
				}
			}
		};

	}

	protected Element createPresenceNotificationItem(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
		Element notification = new Element("presence");
		notification.setAttribute("xmlns", PresencePerNodeExtension.XMLNS_EXTENSION);
		notification.setAttribute("node", node);
		notification.setAttribute("jid", occupantJID.toString());

		if (presenceStanza == null || presenceStanza.getType() == StanzaType.unavailable) {
			notification.setAttribute("type", "unavailable");
		} else if (presenceStanza.getType() == StanzaType.available) {
			notification.setAttribute("type", "available");
		}

		return notification;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { PresencePerNodeExtension.XMLNS_EXTENSION };
	}

	@Override
	public Criteria getModuleCriteria() {
		return null;
	}

	public PresencePerNodeExtension getPresencePerNodeExtension() {
		return presencePerNodeExtension;
	}

	@Override
	public void initialize() {
		eventBus.addHandler("LoginToNode", PubSubComponent.EVENT_XMLNS, loginToNodeHandler);
		eventBus.addHandler("LogoffFromNode", PubSubComponent.EVENT_XMLNS, logoffFromNodeHandler);
		eventBus.addHandler("UpdatePresence", PubSubComponent.EVENT_XMLNS, updatePresenceHandler);
	}

	protected void onLoginToNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
		try {
			Element notification = createPresenceNotificationItem(serviceJID, node, occupantJID, presenceStanza);
			// publish new occupant presence to all occupants
			publish(serviceJID, node, notification);

			// publish presence of all occupants to new occupant
			publishToOne(serviceJID, node, occupantJID);

		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on sending LoginToNodeEvent", e);
		}
	}

	protected void onLogoffFromNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
		try {
			Element notification = createPresenceNotificationItem(serviceJID, node, occupantJID, presenceStanza);

			publish(serviceJID, node, notification);
		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on sending LogoffFromNodeEvent", e);
		}
	}

	protected void onPresenceUpdate(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza) {
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
	}

	protected void publish(BareJID serviceJID, String nodeName, Element itemToSend) throws RepositoryException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJID, nodeName);
		final IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(serviceJID, nodeName);
		final ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJID, nodeName);

		Element items = new Element("items");
		items.addAttribute("node", nodeName);

		Element item = new Element("item");
		items.addChild(item);
		item.addChild(itemToSend);

		publishItemModule.sendNotifications(items, JID.jidInstance(serviceJID), nodeName, nodeConfig, nodeAffiliations,
				nodeSubscriptions);
	}

	protected void publishToOne(BareJID serviceJID, String nodeName, JID destinationJID) throws RepositoryException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJID, nodeName);

		Collection<JID> occupants = presencePerNodeExtension.getNodeOccupants(serviceJID, nodeName);
		for (JID jid : occupants) {

			if (jid.equals(destinationJID))
				continue;

			Packet p = presencePerNodeExtension.getPresence(serviceJID, nodeName, jid);
			if (p == null)
				continue;

			Element items = new Element("items");
			items.addAttribute("node", nodeName);
			Element item = new Element("item");
			items.addChild(item);
			item.addChild(createPresenceNotificationItem(serviceJID, nodeName, jid, p));

			publishItemModule.sendNotifications(new JID[] { destinationJID }, items, JID.jidInstance(serviceJID), nodeConfig,
					nodeName, null);
		}
	}

}
