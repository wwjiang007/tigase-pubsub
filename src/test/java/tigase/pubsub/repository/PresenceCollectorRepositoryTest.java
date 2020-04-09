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
package tigase.pubsub.repository;

import org.junit.Test;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PresenceCollectorRepositoryTest {

	private final PresenceCollectorRepository repository = new PresenceCollectorRepository();

	@Test
	public void test() {
		BareJID recipient = BareJID.bareJIDInstanceNS("recipient@example.com");
		JID sender = JID.jidInstanceNS("sender@example.com/res-1");
		String[] caps = new String[] { "test+notify" };
		String[] preCaps = repository.add(recipient, sender, caps);
		assertTrue(preCaps == null || preCaps.length == 0);

		String[] caps1 = repository.add(recipient, sender, caps);
		assertEquals(caps, caps1);

		assertTrue(repository.remove(recipient, sender));
		String[] caps2 = repository.add(recipient, sender, caps);
		assertTrue(caps2 == null || caps2.length == 0);
	}

}
