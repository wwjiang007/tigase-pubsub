/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2018 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */
package tigase.pubsub.repository.converter;

import org.junit.Test;
import tigase.pubsub.Subscription;

import static org.junit.Assert.*;

public class PubSubNodeConverterTest {

	@Test
	public void getParentTest1() {
		assertEquals("/node1/node2/node3", PubSubNodeConverter.getParent("/node1/node2/node3/leaf"));
	}

	@Test
	public void getParentTestRoot1() {
		assertNull(PubSubNodeConverter.getParent("/"));
	}

	@Test
	public void getParentTestRoot2() {
		assertNull(PubSubNodeConverter.getParent(""));
	}

	@Test
	public void getParentNull() {
		assertNull(PubSubNodeConverter.getParent(null));
	}

	@Test
	public void ParseArrayValue() {
		final String[] strings = PubSubNodeConverter.parseArrayValue("   [   value1   , value2,value3   ]   ");
		assertEquals(3, strings.length);
		assertEquals("value1", strings[0]);
		assertEquals("value2", strings[1]);
		assertEquals("value3", strings[2]);

	}

	@Test
	public void decodeSubscriptionFromDb() {
		assertEquals(Subscription.subscribed, PubSubNodeConverter.decodeSubscription("s:6088692ACAC13"));
		assertEquals(Subscription.subscribed, PubSubNodeConverter.decodeSubscription("s"));
	}
}