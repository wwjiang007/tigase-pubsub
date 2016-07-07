package tigase.pubsub.modules.ext.presence;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.eventbus.EventBus;
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
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

import java.util.Collection;
import java.util.logging.Level;

@Bean(name = "presenceNotifierModule", parent = PubSubComponent.class)
public class PresenceNotifierModule extends AbstractPubSubModule implements Initializable, UnregisterAware {

	@Inject
	private EventBus eventBus;

	@Inject
	private PresencePerNodeExtension presencePerNodeExtension;

	@Inject
	private PublishItemModule publishItemModule;

	public PresenceNotifierModule() {

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
		eventBus.registerAll(this);
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

	@HandleEvent
	public void onLoginToNode(PresencePerNodeExtension.LoginToNodeEvent event) {
		onLoginToNode(event.serviceJID, event.node, event.occupantJID, event.presenceStanza);
	}

	@HandleEvent
	public void onLogoffFromNodeH(PresencePerNodeExtension.LogoffFromNodeEvent event) {
		onLogoffFromNode(event.serviceJID, event.node, event.occupantJID, event.presenceStanza);
	}

	@HandleEvent
	public void onUpdatePresence(PresencePerNodeExtension.UpdatePresenceEvent event) {
		onPresenceUpdate(event.serviceJID, event.node, event.occupantJID, event.presenceStanza);
	}

	@Override
	public void beforeUnregister() {
		eventBus.unregisterAll(this);
	}
}
