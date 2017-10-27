package tigase.pubsub;

import tigase.util.cache.SimpleCache;

public class ListCache<K, V> extends SimpleCache<K, V> {

	public ListCache(int maxSize, long time) {
		super(maxSize, time);
		cache_off = false;
	}

}
