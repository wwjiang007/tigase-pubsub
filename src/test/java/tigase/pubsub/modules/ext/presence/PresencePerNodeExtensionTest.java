package tigase.pubsub.modules.ext.presence;

import org.junit.Before;
import org.junit.Test;
import tigase.eventbus.EventBus;
import tigase.eventbus.EventBusFactory;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Collection;

import static org.junit.Assert.*;

public class PresencePerNodeExtensionTest {

	private static final BareJID service1 = BareJID.bareJIDInstanceNS("service1.local");

	private static final BareJID service2 = BareJID.bareJIDInstanceNS("service2.local");

	private PresencePerNodeExtension ext;

	@Before
	public void setUp() throws Exception {
		final EventBus bus = EventBusFactory.getInstance();

		this.ext = new PresencePerNodeExtension();
		this.ext.setEventBus(bus);
	}

	@Test
	public void testAddPresence01() throws TigaseStringprepException {
		Element presence = new Element("presence");
		presence.setXMLNS(Packet.CLIENT_XMLNS);
		presence.setAttribute("from", "a@b.c/d1");
		presence.setAttribute("to", service1.toString());

		ext.addPresence(service1, "node1", Packet.packetInstance(presence));

		Collection<JID> ocs = ext.getNodeOccupants(service1, "node1");
		assertTrue("Doesn't contains occupant!", ocs.contains(JID.jidInstanceNS("a@b.c/d1")));

		ocs = ext.getNodeOccupants(service2, "node1");
		assertTrue("MUST not contains occupants", ocs.isEmpty());

		assertNotNull("Must contains presence", ext.getPresence(service1, "node1", JID.jidInstanceNS("a@b.c/d1")));
		assertNull("MUST not contain presence", ext.getPresence(service1, "node1", JID.jidInstanceNS("a@b.c/d2")));

		ext.removePresence(service1, "node1", JID.jidInstanceNS("a@b.c/d1"), Packet.packetInstance(presence));

		assertNull("MUST not contain presence", ext.getPresence(service1, "node1", JID.jidInstanceNS("a@b.c/d1")));
		assertTrue(ext.getNodeOccupants(service1, "node1").isEmpty());

	}

	@Test
	public void testAddPresence02() throws TigaseStringprepException {
		Element presence = new Element("presence");
		presence.setXMLNS(Packet.CLIENT_XMLNS);
		presence.setAttribute("from", "a@b.c/d1");
		presence.setAttribute("to", service1.toString());

		ext.addPresence(service1, "node1", Packet.packetInstance(presence));
		ext.addPresence(service1, "node2", Packet.packetInstance(presence));
		ext.addPresence(service1, "node3", Packet.packetInstance(presence));

		ext.removePresence(service1, null, JID.jidInstanceNS("a@b.c/d1"), Packet.packetInstance(presence));

		assertNull("MUST not contain presence", ext.getPresence(service1, "node1", JID.jidInstanceNS("a@b.c/d1")));
		assertTrue(ext.getNodeOccupants(service1, "node1").isEmpty());
	}

}
