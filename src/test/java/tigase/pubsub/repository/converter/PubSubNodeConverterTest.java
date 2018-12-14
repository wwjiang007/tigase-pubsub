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