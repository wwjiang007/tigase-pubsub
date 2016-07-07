package tigase.pubsub.modules.ext.presence;

import tigase.eventbus.EventBus;
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.modules.PresenceCollectorModule;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "presencePerNodeExtension", parent = PubSubComponent.class)
public class PresencePerNodeExtension implements Initializable, UnregisterAware {

	public static class LoginToNodeEvent {

		public final String node;

		public final JID occupantJID;

		public final Packet presenceStanza;

		public final BareJID serviceJID;

		public LoginToNodeEvent(BareJID serviceJID, String node, Packet presenceStanza) {
			this.occupantJID = presenceStanza.getStanzaFrom();
			this.node = node;
			this.presenceStanza = presenceStanza;
			this.serviceJID = serviceJID;
		}
	}

	public static class LogoffFromNodeEvent {

		public final String node;

		public final JID occupantJID;

		public final Packet presenceStanza;

		public final BareJID serviceJID;

		public LogoffFromNodeEvent(BareJID serviceJID, String node, JID occupandJID, Packet presenceStanza) {
			this.occupantJID = occupandJID;
			this.node = node;
			this.presenceStanza = presenceStanza;
			this.serviceJID = serviceJID;
		}
	}

	public static class UpdatePresenceEvent {

		public final String node;

		public final JID occupantJID;

		public final Packet presenceStanza;

		public final BareJID serviceJID;

		public UpdatePresenceEvent(BareJID serviceJID, String node, Packet presenceStanza) {
			this.occupantJID = presenceStanza.getStanzaFrom();
			this.node = node;
			this.presenceStanza = presenceStanza;
			this.serviceJID = serviceJID;
		}

	}

	public static final String XMLNS_EXTENSION = "tigase:pubsub:1";

	@Inject
	private EventBus eventBus;

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * (ServiceJID, (NodeName, {OccupantJID}))
	 */
	private final Map<BareJID, Map<String, Set<JID>>> occupants = new ConcurrentHashMap<BareJID, Map<String, Set<JID>>>();

	/**
	 * (OccupantBareJID, (Resource, (ServiceJID, (PubSubNodeName,
	 * PresencePacket))))
	 */
	private final Map<BareJID, Map<String, Map<BareJID, Map<String, Packet>>>> presences = new ConcurrentHashMap<BareJID, Map<String, Map<BareJID, Map<String, Packet>>>>();

	@Inject
	private PubSubConfig pubsubContext;

	void addJidToOccupants(BareJID serviceJID, String nodeName, JID jid) {
		Map<String, Set<JID>> services = occupants.get(serviceJID);
		if (services == null) {
			services = new ConcurrentHashMap<String, Set<JID>>();
			occupants.put(serviceJID, services);
		}
		Set<JID> occs = services.get(nodeName);
		if (occs == null) {
			occs = new HashSet<JID>();
			services.put(nodeName, occs);
		}
		occs.add(jid);
	}

	void addPresence(BareJID serviceJID, String nodeName, Packet packet) {
		final JID sender = packet.getStanzaFrom();

		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(sender.getBareJID());
		if (resources == null) {
			resources = new ConcurrentHashMap<String, Map<BareJID, Map<String, Packet>>>();
			this.presences.put(sender.getBareJID(), resources);
		}

		Map<BareJID, Map<String, Packet>> services = resources.get(sender.getResource());
		if (services == null) {
			services = new ConcurrentHashMap<BareJID, Map<String, Packet>>();
			resources.put(sender.getResource(), services);
		}

		Map<String, Packet> nodesPresence = services.get(serviceJID);
		if (nodesPresence == null) {
			nodesPresence = new ConcurrentHashMap<String, Packet>();
			services.put(serviceJID, nodesPresence);
		}

		boolean isUpdate = nodesPresence.containsKey(nodeName);
		nodesPresence.put(nodeName, packet);
		addJidToOccupants(serviceJID, nodeName, sender);

		if (isUpdate) {
			eventBus.fire(new UpdatePresenceEvent(serviceJID, nodeName, packet));
		} else {
			eventBus.fire(new LoginToNodeEvent(serviceJID, nodeName, packet));
		}
	}

	public EventBus getEventBus() {
		return eventBus;
	}

	public Collection<JID> getNodeOccupants(BareJID serviceJID, String nodeName) {
		Map<String, Set<JID>> services = occupants.get(serviceJID);
		if (services == null)
			return Collections.emptyList();
		Set<JID> occs = services.get(nodeName);
		if (occs == null)
			return Collections.emptyList();
		return Collections.unmodifiableCollection(occs);
	}

	public Collection<String> getNodes(BareJID serviceJID, JID occupantJID) {
		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(occupantJID.getBareJID());
		if (resources == null)
			return Collections.emptyList();

		Map<BareJID, Map<String, Packet>> x = resources.get(occupantJID.getResource());

		if (x == null)
			return Collections.emptyList();

		Map<String, Packet> p = x.get(serviceJID);

		return Collections.unmodifiableCollection(p.keySet());
	}

	public Collection<Packet> getPresence(BareJID serviceJID, String nodeName, BareJID occupantJID) {
		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(occupantJID);
		if (resources == null)
			return Collections.emptyList();

		Set<Packet> prs = new HashSet<Packet>();

		for (Map<BareJID, Map<String, Packet>> services : resources.values()) {
			Map<String, Packet> nodes = services.get(serviceJID);
			if (nodes != null && nodes.containsKey(nodeName)) {
				prs.add(nodes.get(nodeName));
			}
		}

		return prs;
	}

	public Packet getPresence(BareJID serviceJID, String nodeName, JID occupantJID) {
		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(occupantJID.getBareJID());
		if (resources == null)
			return null;
		Map<BareJID, Map<String, Packet>> services = resources.get(occupantJID.getResource());
		if (services == null)
			return null;
		Map<String, Packet> nodes = services.get(serviceJID);
		if (nodes == null)
			return null;
		return nodes.get(nodeName);
	}

	@Override
	public void initialize() {
		eventBus.registerAll(this);
	}

	private void intProcessLogoffFrom(BareJID serviceJID, JID sender, Map<String, Packet> nodes, Packet presenceStanza) {
		if (nodes == null)
			return;
		for (String node : nodes.keySet()) {
			removeJidFromOccupants(serviceJID, node, sender);

			eventBus.fire(new LogoffFromNodeEvent(serviceJID, node, sender, presenceStanza));
		}
	}

	@HandleEvent
	public void onPresenceEvent(PresenceCollectorModule.PresenceChangeEvent event) {
		process(event.packet);
	}

	protected void process(Packet packet) {
		final StanzaType type = packet.getType();
		final BareJID serviceJID = packet.getStanzaTo().getBareJID();

		Element pubsubExtElement = packet.getElement().getChild("pubsub", XMLNS_EXTENSION);
		if (pubsubExtElement != null) {
			final String nodeName = pubsubExtElement.getAttributeStaticStr("node");

			if (type == null || type == StanzaType.available) {
				addPresence(serviceJID, nodeName, packet);
			} else if (StanzaType.unavailable == type) {
				removePresence(serviceJID, nodeName, packet.getStanzaFrom(), packet);
			}
		} else if (type == StanzaType.unavailable) {
			Collection<String> nds = getNodes(serviceJID, packet.getStanzaFrom());
			for (String nodeName : nds) {
				removePresence(serviceJID, nodeName, packet.getStanzaFrom(), packet);
			}
		}
	}

	void removeJidFromOccupants(BareJID serviceJID, String node, JID jid) {
		Map<String, Set<JID>> services = occupants.get(serviceJID);
		if (services != null) {
			Set<JID> occs = services.get(node);
			if (occs != null) {
				occs.remove(jid);
				if (occs.isEmpty()) {
					occupants.remove(node);
				}
			}
			if (services.isEmpty())
				occupants.remove(serviceJID);
		}
	}

	void removePresence(BareJID serviceJID, String nodeName, JID sender, Packet presenceStanza) {
		if (sender.getResource() == null) {
			if (log.isLoggable(Level.WARNING))
				log.warning("Skip processing presence from BareJID " + sender);
		} else {
			// resource gone
			Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(sender.getBareJID());
			if (resources != null) {
				Map<BareJID, Map<String, Packet>> services = resources.get(sender.getResource());
				if (services != null) {
					Map<String, Packet> nodes = services.get(serviceJID);
					if (nodes != null && nodeName != null) {
						// manual logoff from specific node
						nodes.remove(nodeName);
						removeJidFromOccupants(serviceJID, nodeName, sender);

						Element event = new Element("LogoffFromNode", new String[] { "xmlns" },
								new String[] { PubSubComponent.EVENT_XMLNS });
						event.addChild(new Element("service", serviceJID.toString()));
						event.addChild(new Element("node", nodeName));
						event.addChild(new Element("sender", sender.toString()));
						event.addChild(presenceStanza.getElement());
						eventBus.fire(event);

						if (nodes.isEmpty())
							services.remove(serviceJID);

					} else if (nodes != null) {
						// resource is gone. logoff from all nodes
						Map<String, Packet> removed = services.remove(serviceJID);
						intProcessLogoffFrom(serviceJID, sender, removed, presenceStanza);
					}
					if (services.isEmpty())
						resources.remove(sender.getResource());
				}
				if (resources.isEmpty())
					this.presences.remove(sender.getBareJID());
			}
		}
	}

	public void setEventBus(EventBus eventBus) {
		this.eventBus = eventBus;
	}

	@Override
	public void beforeUnregister() {
		eventBus.unregisterAll(this);
	}
}
