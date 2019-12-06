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
package tigase.pubsub;

import tigase.kernel.beans.Bean;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.xmpp.jid.JID;

@Bean(name = "packetHashCodeGenerator", parent = PubSubComponent.class, active = true)
public class DefaultPacketHashCodeGenerator implements PubSubComponent.PacketHashCodeGenerator {

	@Override
	public int hashCodeForPacket(Packet packet) {
		if (packet.getElemName() == Presence.ELEM_NAME) {
			JID from = packet.getStanzaFrom();
			if (from != null && from.getResource() != null) {
				return packet.getStanzaFrom().hashCode();
			} else {
				JID to = packet.getStanzaTo();
				if (to != null) {
					return to.hashCode();
				}
			}
		}
		return packet.hashCode();
	}
}
