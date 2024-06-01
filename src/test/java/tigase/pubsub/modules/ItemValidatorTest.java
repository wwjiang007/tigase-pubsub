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

import org.junit.Test;
import tigase.util.Base64;
import tigase.xml.Element;

import java.util.Random;

import static org.junit.Assert.*;

public class ItemValidatorTest {

	@Test
	public void extractIdentityKey() {
		Random random = new Random();
		byte[] key = new byte[16];
		random.nextBytes(key);
		String identityKey = Base64.encode(key);
		Element item = new Element("item").withElement("bundle", "eu.siacs.conversations.axolotl", bundleEl -> {
			bundleEl.withElement("identityKey", null, identityKey);
		});
		ItemValidator itemValidator = new ItemValidator();
		assertEquals(identityKey, itemValidator.extractIdentityKey(item));
	}
}