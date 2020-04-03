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
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;

@Bean(name = "presenceNotifierModule", parent = PubSubComponent.class, active = true)
public class PresenceNotifierModule
		extends AbstractPubSubModule
		implements Initializable, UnregisterAware {

	@Inject
	private EventBus eventBus;

	@Inject
	private PresencePerNodeExtension presencePerNodeExtension;

	@Inject
	private PublishItemModule publishItemModule;

	public PresenceNotifierModule() {

	}

	@Override
	public String[] getFeatures() {
		return new String[]{PresencePerNodeExtension.XMLNS_EXTENSION};
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

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
	}

	@HandleEvent
	public void onLoginToNode(PresencePerNodeExtension.LoginToNodeEvent event) {
		if (!event.componentName.equals(config.getComponentName())) {
			return;
		}
		onLoginToNode(event.serviceJID, event.node, event.occupantJID, event.presenceStanza);
	}

	@HandleEvent
	public void onLogoffFromNodeH(PresencePerNodeExtension.LogoffFromNodeEvent event) {
		if (!event.componentName.equals(config.getComponentName())) {
			return;
		}
		onLogoffFromNode(event.serviceJID, event.node, event.occupantJID, event.presenceStanza);
	}

	@HandleEvent
	public void onUpdatePresence(PresencePerNodeExtension.UpdatePresenceEvent event) {
		if (!event.componentName.equals(config.getComponentName())) {
			return;
		}
		onPresenceUpdate(event.serviceJID, event.node, event.occupantJID, event.presenceStanza);
	}

	@Override
	public void beforeUnregister() {
		eventBus.unregisterAll(this);
	}

	protected Element createPresenceNotificationItem(BareJID serviceJID, String node, JID occupantJID,
													 Packet presenceStanza) {
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

	protected void publish(BareJID serviceJID, String nodeName, Element itemToSend) throws RepositoryException {
		Element item = new Element("item");
		item.addChild(itemToSend);

		publishItemModule.generateNotifications(serviceJID, nodeName, Collections.singletonList(item), null, false);
	}

	protected void publishToOne(BareJID serviceJID, String nodeName, JID destinationJID) throws RepositoryException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJID, nodeName);

		Collection<JID> occupants = presencePerNodeExtension.getNodeOccupants(serviceJID, nodeName);
		if (occupants.contains(destinationJID)) {
			Packet p = presencePerNodeExtension.getPresence(serviceJID, nodeName, destinationJID);
			if (p == null) {
				return;
			}

			Element item = new Element("item");
			item.addChild(createPresenceNotificationItem(serviceJID, nodeName, destinationJID, p));

			publishItemModule.sendNotification(serviceJID, nodeName, item, null, null, destinationJID, StanzaType.headline);
		}
	}
}
