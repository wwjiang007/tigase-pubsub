/*
 * CapsModule.java
 *
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tigase.pubsub.modules;

import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubComponent;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.impl.PresenceCapabilitiesManager;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.HashSet;

/**
 * @author andrzej
 */
@Bean(name = "capsModule", parent = PubSubComponent.class, active = true)
public class CapsModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = new Or(ElementCriteria.nameType("iq", "result")
														.add(ElementCriteria.name("query",
																				  "http://jabber.org/protocol/disco#info")),
												ElementCriteria.nameType("iq", "error")
														.add(ElementCriteria.name("query",
																				  "http://jabber.org/protocol/disco#info")));

	private static String[] FEATURES = {};

	@Inject
	private EventBus eventBus;

	public CapsModule() {
	}

	@Override
	public String[] getFeatures() {
		return FEATURES;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		PresenceCapabilitiesManager.processCapsQueryResponse(packet);
		if (packet.getType() == StanzaType.result) {
			String id = packet.getAttributeStaticStr("id");
			if (id == null)
				return;
			BareJID serviceJid = id.contains("@") ? BareJID.bareJIDInstance(id) : packet.getStanzaTo().getBareJID();
			Element query = packet.getElement().getChild("query", DiscoveryModule.DISCO_INFO_XMLNS);
			if (query != null) {
				String node = query.getAttributeStaticStr("node");
				if (node != null) {
					String[] features = PresenceCapabilitiesManager.getNodeFeatures(node);
					if (features != null) {
						eventBus.fire(new PresenceCollectorModule.CapsChangeEvent(serviceJid, packet.getStanzaFrom(), new String[] { node }, EMPTY_FEATURES,
																				  new HashSet<>(Arrays.asList(features))));
					}
				}
			}
		}
	}

	/**
	 * Processes presence packet and send disco#info queries when needed
	 *
	 * @param packet
	 *
	 * @return
	 */
	public String[] processPresence(Packet packet) {
		String[] caps = null;
		Element c = packet.getElement().getChildStaticStr("c");
		if (c != null) {
			final JID jid = packet.getStanzaFrom();
			caps = PresenceCapabilitiesManager.processPresence(c);
			if (caps != null) {
				Arrays.sort(caps);

				JID pubSubJid = packet.getStanzaTo();
				if (pubSubJid.getLocalpart() != null) {
					String compName = config.getComponentJID().getLocalpart();
					pubSubJid = JID.jidInstanceNS(compName + "." + pubSubJid.getDomain());
				}

				for (String node : caps) {
					if (PresenceCapabilitiesManager.getNodeFeatures(node) == null) {
						Packet p = PresenceCapabilitiesManager.prepareCapsQuery(jid, pubSubJid, node);
						p.getElement().setAttribute("id", packet.getStanzaTo().getBareJID().toString());
						packetWriter.write(p);
					}
				}
			}
		}
		return caps;
	}

}
