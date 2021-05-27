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

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.PresenceCollectorRepository;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.impl.PresenceCapabilitiesManager;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Bean(name = "presenceCollectorModule", parent = PubSubComponent.class, active = true)
public class PresenceCollectorModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.name("presence");
	@Inject
	private CapsModule capsModule;
	@Inject
	private EventBus eventBus;
	@Inject
	private PresenceCollectorRepository presenceByService;

	public boolean addJid(final BareJID serviceJid, final JID jid, String caps) {
		if (jid == null) {
			return false;
		}
		
		String oldCaps = presenceByService.add(serviceJid, jid, caps);
		boolean added = oldCaps == null;
		log.finest("for service " + serviceJid + " - Contact " + jid + " is collected.");

		// we are firing CapsChangeEvent only for PEP services
		if (this.config.isPepPeristent() && this.config.isSendLastPublishedItemOnPresence() &&
				(serviceJid.getLocalpart() != null || config.isSubscribeByPresenceFilteredNotifications()) &&
				oldCaps != caps && caps != null) {
			// calculating new features and firing event

			Set<String> newFeatures = Collections.emptySet();

			if (!Objects.equals(oldCaps, caps)) {
				if (caps != null) {
					String[] features = caps != null ? PresenceCapabilitiesManager.getNodeFeatures(caps) : null;
					String[] oldFeatures = oldCaps != null ? PresenceCapabilitiesManager.getNodeFeatures(oldCaps) : null;
					if (features != null) {
						Stream<String> featuresStream = Arrays.stream(features);
						if (oldFeatures != null) {
							// if old features exist, remove filter out features which exist in old features
							featuresStream = featuresStream.filter(f -> Arrays.binarySearch(oldFeatures, f) < 0);
						}
						newFeatures = featuresStream.collect(Collectors.toSet());
					}
				}
			}

			if (!newFeatures.isEmpty()) {
				fireCapsChangeEvent(serviceJid, jid, caps, oldCaps, newFeatures);
			}
		}

		return added;
	}

	@Override
	public boolean canHandle(Packet packet) {
		if (packet.getStanzaTo() == null || packet.getStanzaTo().getResource() != null) {
			return false;
		}
		return super.canHandle(packet);
	}

	public List<JID> getAllAvailableJids(final BareJID serviceJid) {
		return presenceByService.getAllAvailableJids(serviceJid, (x) -> true)
				.filter(this::isAvailableLocally)
				.collect(Collectors.toList());
	}

	public List<JID> getAllAvailableJidsWithFeature(final BareJID serviceJid, final String feature) {
		Set<String> nodesWithFeature = PresenceCapabilitiesManager.getNodesWithFeature(feature);
		return presenceByService.getAllAvailableJids(serviceJid, nodesWithFeature::contains)
				.collect(Collectors.toList());
	}

	public List<JID> getAllAvailableResources(final BareJID serviceJid, final BareJID bareJid) {
		return presenceByService.getAllAvailableResources(serviceJid, bareJid)
				.stream()
				.filter(this::isAvailableLocally)
				.collect(Collectors.toList());
	}

	@Override
	public String[] getFeatures() {
		if (config.isSubscribeByPresenceFilteredNotifications()) {
			return new String[]{"http://jabber.org/protocol/pubsub#presence-notifications", "http://jabber.org/protocol/pubsub#filtered-notifications"};
		}
		return new String[]{"http://jabber.org/protocol/pubsub#presence-notifications"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	public boolean isJidAvailable(final BareJID serviceJid, final BareJID bareJid) {
		return presenceByService.isAvailable(serviceJid, bareJid);
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		final StanzaType type = packet.getType();
		final JID jid = packet.getStanzaFrom();
		final JID toJid = packet.getStanzaTo();
		if (jid == null || toJid == null) {
			return;
		}
		// why it is here if it is also below?
		// PresenceChangeEvent event = new PresenceChangeEvent( packet );
		// config.getEventBus().fire( event, this );

		if (type == null || type == StanzaType.available) {
			String[] caps = config.isPepPeristent() ? capsModule.processPresence(packet) : null;
			boolean added = addJid(toJid.getBareJID(), jid, caps == null || caps.length == 0 ? null : caps[0]);
			firePresenceChangeEvent(packet);
			if (added && packet.getStanzaTo().getLocalpart() == null) {
				Packet p = new Presence(new Element("presence", new String[]{"to", "from", Packet.XMLNS_ATT},
													new String[]{jid.toString(), toJid.toString(),
																 Packet.CLIENT_XMLNS}), toJid, jid);

				packetWriter.write(p);
			}
		} else if (StanzaType.unavailable == type) {
			removeJid(toJid.getBareJID(), jid);
			firePresenceChangeEvent(packet);
			if (packet.getStanzaTo().getLocalpart() == null) {
				Packet p = new Presence(new Element("presence", new String[]{"to", "from", "type", Packet.XMLNS_ATT},
													new String[]{jid.toString(), toJid.toString(),
																 StanzaType.unavailable.toString(),
																 Packet.CLIENT_XMLNS}), toJid, jid);

				packetWriter.write(p);
			}
		} else if (StanzaType.subscribe == type) {
			log.finest("Contact " + jid + " wants to subscribe PubSub");

			Packet presence = preparePresence(packet, StanzaType.subscribed);

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, StanzaType.subscribe);
			if (presence != null) {
				packetWriter.write(presence);
			}
		} else if (StanzaType.unsubscribe == type || StanzaType.unsubscribed == type) {
			log.finest("Contact " + jid + " wants to unsubscribe PubSub");

			Packet presence = preparePresence(packet, StanzaType.unsubscribed);

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, StanzaType.unsubscribe);
			if (presence != null) {
				packetWriter.write(presence);
			}
		}

	}

	protected boolean isAvailableLocally(JID jid) {
		return true;
	}

	protected boolean removeJid(final BareJID serviceJid, final JID jid) {
		if (jid == null) {
			return false;
		}

		boolean removed = presenceByService.remove(serviceJid, jid);

		return removed;
	}

	private void fireCapsChangeEvent(BareJID serviceJid, JID jid, String caps, String oldCaps,
									 Set<String> newFeatures) {
		eventBus.fire(new CapsChangeEvent(config.getComponentName(), serviceJid, jid, caps, oldCaps, newFeatures));
	}

	private void firePresenceChangeEvent(Packet packet) {
		eventBus.fire(new PresenceChangeEvent(config.getComponentName(), packet));
	}

	private Packet preparePresence(final Packet presence, StanzaType type) {
		JID to = presence.getTo();
		JID from = presence.getStanzaFrom();

		if (from != null && to != null && !((from.getBareJID()).equals(to.getBareJID()))) {
			JID jid = from.copyWithoutResource();
			Element p = new Element("presence", new String[]{"to", "from", Packet.XMLNS_ATT},
									new String[]{jid.toString(), to.toString(), Packet.CLIENT_XMLNS});

			if (type != null) {
				p.setAttribute("type", type.toString());
			}

			return new Presence(p, to, from);
		}

		return null;
	}

	public static class CapsChangeEvent {

		public final String componentName;
		public final JID buddyJid;
		public final String newCaps;
		public final Set<String> newFeatures;
		public final String oldCaps;
		public final BareJID serviceJid;

		public CapsChangeEvent(String componentName, BareJID serviceJid, JID buddyJid, String newCaps, String oldCaps,
							   Set<String> newFeatures) {
			this.componentName = componentName;
			this.serviceJid = serviceJid;
			this.buddyJid = buddyJid;
			this.newCaps = newCaps;
			this.oldCaps = oldCaps;
			this.newFeatures = newFeatures;
		}

	}

	public static class PresenceChangeEvent {

		public final Packet packet;
		public final String componentName;

		public PresenceChangeEvent(String componentName, Packet packet) {
			this.componentName = componentName;
			this.packet = packet;
		}

	}

}
