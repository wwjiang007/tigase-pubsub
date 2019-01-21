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
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "presencePerNodeExtension", parent = PubSubComponent.class, active = true)
public class PresencePerNodeExtension
		implements Initializable, UnregisterAware {

	public static final String XMLNS_EXTENSION = "tigase:pubsub:1";
	protected final Logger log = Logger.getLogger(this.getClass().getName());
	/**
	 * (ServiceJID, (NodeName, {OccupantJID}))
	 */
	private final Map<BareJID, Map<String, Set<JID>>> occupants = new ConcurrentHashMap<BareJID, Map<String, Set<JID>>>();
	/**
	 * (OccupantBareJID, (Resource, (ServiceJID, (PubSubNodeName, PresencePacket))))
	 */
	private final Map<BareJID, Map<String, Map<BareJID, Map<String, Packet>>>> presences = new ConcurrentHashMap<BareJID, Map<String, Map<BareJID, Map<String, Packet>>>>();
	@Inject
	private EventBus eventBus;
	@Inject
	private PubSubConfig pubsubContext;

	public EventBus getEventBus() {
		return eventBus;
	}

	public void setEventBus(EventBus eventBus) {
		this.eventBus = eventBus;
	}

	public Collection<JID> getNodeOccupants(BareJID serviceJID, String nodeName) {
		Map<String, Set<JID>> services = occupants.get(serviceJID);
		if (services == null) {
			return Collections.emptyList();
		}
		Set<JID> occs = services.get(nodeName);
		if (occs == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableCollection(occs);
	}

	public Collection<String> getNodes(BareJID serviceJID, JID occupantJID) {
		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(occupantJID.getBareJID());
		if (resources == null) {
			return Collections.emptyList();
		}

		Map<BareJID, Map<String, Packet>> x = resources.get(occupantJID.getResource());

		if (x == null) {
			return Collections.emptyList();
		}

		Map<String, Packet> p = x.get(serviceJID);

		return Collections.unmodifiableCollection(p.keySet());
	}

	public Collection<Packet> getPresence(BareJID serviceJID, String nodeName, BareJID occupantJID) {
		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(occupantJID);
		if (resources == null) {
			return Collections.emptyList();
		}

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
		if (resources == null) {
			return null;
		}
		Map<BareJID, Map<String, Packet>> services = resources.get(occupantJID.getResource());
		if (services == null) {
			return null;
		}
		Map<String, Packet> nodes = services.get(serviceJID);
		if (nodes == null) {
			return null;
		}
		return nodes.get(nodeName);
	}

	@Override
	public void initialize() {
		eventBus.registerAll(this);
	}

	@HandleEvent
	public void onPresenceEvent(PresenceCollectorModule.PresenceChangeEvent event) {
		process(event.packet);
	}

	@Override
	public void beforeUnregister() {
		eventBus.unregisterAll(this);
	}

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
			if (services.isEmpty()) {
				occupants.remove(serviceJID);
			}
		}
	}

	void removePresence(BareJID serviceJID, String nodeName, JID sender, Packet presenceStanza) {
		if (sender.getResource() == null) {
			if (log.isLoggable(Level.WARNING)) {
				log.warning("Skip processing presence from BareJID " + sender);
			}
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

						Element event = new Element("LogoffFromNode", new String[]{"xmlns"},
													new String[]{PubSubComponent.EVENT_XMLNS});
						event.addChild(new Element("service", serviceJID.toString()));
						event.addChild(new Element("node", nodeName));
						event.addChild(new Element("sender", sender.toString()));
						event.addChild(presenceStanza.getElement());
						eventBus.fire(event);

						if (nodes.isEmpty()) {
							services.remove(serviceJID);
						}

					} else if (nodes != null) {
						// resource is gone. logoff from all nodes
						Map<String, Packet> removed = services.remove(serviceJID);
						intProcessLogoffFrom(serviceJID, sender, removed, presenceStanza);
					}
					if (services.isEmpty()) {
						resources.remove(sender.getResource());
					}
				}
				if (resources.isEmpty()) {
					this.presences.remove(sender.getBareJID());
				}
			}
		}
	}

	protected void process(Packet packet) {
		final StanzaType type = packet.getType();
		final BareJID serviceJID = packet.getStanzaTo().getBareJID();
		final JID stanzaFrom = packet.getStanzaFrom();

		if (stanzaFrom == null) {
			return;
		}

		Element pubsubExtElement = packet.getElement().getChild("pubsub", XMLNS_EXTENSION);
		if (pubsubExtElement != null) {
			final String nodeName = pubsubExtElement.getAttributeStaticStr("node");

			if (type == null || type == StanzaType.available) {
				addPresence(serviceJID, nodeName, packet);
			} else if (StanzaType.unavailable == type) {
				removePresence(serviceJID, nodeName, stanzaFrom, packet);
			}
		} else if (type == StanzaType.unavailable) {
			Collection<String> nds = getNodes(serviceJID, stanzaFrom);
			for (String nodeName : nds) {
				removePresence(serviceJID, nodeName, stanzaFrom, packet);
			}
		}
	}

	private void intProcessLogoffFrom(BareJID serviceJID, JID sender, Map<String, Packet> nodes,
									  Packet presenceStanza) {
		if (nodes == null) {
			return;
		}
		for (String node : nodes.keySet()) {
			removeJidFromOccupants(serviceJID, node, sender);

			eventBus.fire(new LogoffFromNodeEvent(serviceJID, node, sender, presenceStanza));
		}
	}

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
}
