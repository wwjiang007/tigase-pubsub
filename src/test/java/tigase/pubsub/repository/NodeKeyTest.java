package tigase.pubsub.repository;

import org.junit.Test;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xmpp.jid.BareJID;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Created by andrzej on 12.10.2016.
 */
public class NodeKeyTest {

	@Test
	public void testEquality() throws TigaseStringprepException {
		CachedPubSubRepository.NodeKey key1 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("TeSt@example.com"), "Test");
		CachedPubSubRepository.NodeKey key2 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("test@example.com"), "Test");
		CachedPubSubRepository.NodeKey key3 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("test@example.com"), "test");
		CachedPubSubRepository.NodeKey key4 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("test1@example.com"), "Test");

		assertEquals(key1.hashCode(), key2.hashCode());
		assertTrue(key1.equals(key2));

		assertNotEquals(key1.hashCode(), key3.hashCode());
		assertFalse(key1.equals(key3));

		assertNotEquals(key1.hashCode(), key4.hashCode());
		assertFalse(key1.equals(key4));
	}

	@Test
	public void testMapKeyUsage() throws TigaseStringprepException {
		CachedPubSubRepository.NodeKey key1 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("TeSt@example.com"), "Test");
		CachedPubSubRepository.NodeKey key2 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("test@example.com"), "Test");
		CachedPubSubRepository.NodeKey key3 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("test@example.com"), "test");
		CachedPubSubRepository.NodeKey key4 = new CachedPubSubRepository.NodeKey(BareJID.bareJIDInstance("test1@example.com"), "Test");

		Map<CachedPubSubRepository.NodeKey,UUID> map = new HashMap<>();
		UUID uid1 = UUID.randomUUID();
		map.put(key1, uid1);
		assertEquals(uid1, map.get(key1));
		UUID uid2 = UUID.randomUUID();
		map.put(key2, uid2);
		UUID uid3 = UUID.randomUUID();
		map.put(key3, uid3);
		UUID uid4 = UUID.randomUUID();
		map.put(key4, uid4);

		assertEquals(uid2, map.get(key1));
		assertEquals(uid2, map.get(key2));
		assertEquals(uid3, map.get(key3));
		assertEquals(uid4, map.get(key4));
	}


}
