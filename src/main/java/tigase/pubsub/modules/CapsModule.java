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
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tigase.pubsub.modules;

import tigase.component2.PacketWriter;
import tigase.component2.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.impl.PresenceCapabilitiesManager;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 *
 * @author andrzej
 */
public class CapsModule extends AbstractPubSubModule {
	
	private static final Criteria CRIT = new Or(
			ElementCriteria.nameType("iq", "result").add(ElementCriteria.name("query", "http://jabber.org/protocol/disco#info")),
			ElementCriteria.nameType("iq", "error").add(ElementCriteria.name("query", "http://jabber.org/protocol/disco#info"))
		);
	
	private static String[] FEATURES = {};
	
	public CapsModule(PubSubConfig config, PacketWriter packetWriter) {
		super(config, packetWriter);
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
	}
	
	/**
	 * Processes presence packet and send disco#info queries when needed
	 * 
	 * @param packet
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
			}

			Queue<Packet> results = new ArrayDeque<>();
			JID pubSubJid = packet.getStanzaTo();
			if (pubSubJid.getLocalpart() != null) {
				String compName = config.getComponentJID().getLocalpart();
				pubSubJid = JID.jidInstanceNS(compName + "." + pubSubJid.getDomain());
			}

			PresenceCapabilitiesManager.prepareCapsQueries(pubSubJid, jid, caps, results);
			packetWriter.write(results);
		}
		return caps;
	}
	
}
