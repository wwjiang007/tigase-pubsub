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

package tigase.pubsub;

import java.util.UUID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
public class PubSubComponentTest {
	
	String name = null;
	PubSubComponent pubsub = null;
	String from = null;
	String to = null;
	Packet packet = null;
	
	@Before
	public void setup() throws TigaseStringprepException {
		name = "pubsub";
		pubsub =  new PubSubComponent();
		pubsub.setName(name);

		from = "test@test/" + UUID.randomUUID();
	}
	
	private void createPacket() throws TigaseStringprepException {
		packet = Packet.packetInstance(new Element("iq", new String[] { "from", "to", "type", Packet.XMLNS_ATT }, new String[] { from, to, "set", Packet.CLIENT_XMLNS }));
		packet.setPacketFrom(JID.jidInstanceNS(from));
		packet.setPacketTo(null);
	}
	
	@After
	public void teardown() {
		pubsub = null;
		from = null;
		to = null;
		packet = null;
	}
	
//	@Test
//	public void testHashCodeForPacketTo1() throws TigaseStringprepException {	
//		to = name + ".example.com";		
//		createPacket();
//		
//		Assert.assertEquals("'to' used as source for packet hash code, should use 'from'", from.hashCode(), pubsub.hashCodeForPacket(packet));
//	}
//	
//	@Test
//	public void testHashCodeForPacketFrom1() throws TigaseStringprepException {		
//		to = name + ".example.com";		
//		createPacket();
//		
//		packet = packet.okResult((String) null, 0);
//		// I know I'm checking with from but packet in okResult swaps from with to!
//		Assert.assertEquals("'from' used as source for packet hash code, should use 'to'", from.hashCode(), pubsub.hashCodeForPacket(packet));
//	}
//
//	@Test
//	public void testHashCodeForPacketTo2() throws TigaseStringprepException {	
//		to = name + "@example.com";		
//		createPacket();
//		
//		Assert.assertEquals("'to' used as source for packet hash code, should use 'from'", from.hashCode(), pubsub.hashCodeForPacket(packet));
//	}
//	
//	@Test
//	public void testHashCodeForPacketFrom2() throws TigaseStringprepException {		
//		to = name + "@example.com";		
//		createPacket();		
//		// in this case packetTo is set to componentId
//		packet.setPacketTo(pubsub.getComponentId());
//		packet = packet.okResult((String) null, 0);
//		// I know I'm checking with from but packet in okResult swaps from with to!
//		Assert.assertEquals("'from' used as source for packet hash code, should use 'to'", from.hashCode(), pubsub.hashCodeForPacket(packet));
//	}
	
}
