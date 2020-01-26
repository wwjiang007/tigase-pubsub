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

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.jid.BareJID;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Implementation of PubSub node subscription handling.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:27:46 GMT
 */
public abstract class NodeSubscriptions
		implements ISubscriptions {

	protected final static String DELIMITER = ";";
	protected final Logger log = Logger.getLogger(this.getClass().getName());
	// protected final FragmentedMap<BareJID, UsersSubscription> subs = new
	// FragmentedMap<BareJID, UsersSubscription>(
	// MAX_FRAGMENT_SIZE);
	protected final ConcurrentMap<BareJID, UsersSubscription> subs = new ConcurrentHashMap<BareJID, UsersSubscription>();
	// public final static int MAX_FRAGMENT_SIZE = 10000;

	public static tigase.pubsub.repository.cached.NodeSubscriptions create() {
		tigase.pubsub.repository.cached.NodeSubscriptions s = new tigase.pubsub.repository.cached.NodeSubscriptions();

		return s;
	}

	protected NodeSubscriptions() {
	}

	protected NodeSubscriptions(Map<BareJID, UsersSubscription> subscriptions) {
		if (subscriptions != null) {
			subs.putAll(subscriptions);
		}
	}

	@Override
	public String addSubscriberJid(final BareJID bareJid, final Subscription subscription) {
		final String subid = Utils.createUID(bareJid);
		UsersSubscription s = new UsersSubscription(bareJid, subid, subscription);

		subs.put(bareJid, s);

		return subid;
	}

	@Override
	public void changeSubscription(BareJID bareJid, Subscription subscription) {
		UsersSubscription s = get(bareJid);

		if (s != null) {
			s.setSubscription(subscription);
		}
	}

	@Override
	public Subscription getSubscription(BareJID bareJid) {
		UsersSubscription s = get(bareJid);

		if (s != null) {
			return s.getSubscription();
		}

		return Subscription.none;
	}

	@Override
	public String getSubscriptionId(BareJID bareJid) {
		UsersSubscription s = get(bareJid);

		if (s != null) {
			return s.getSubid();
		}

		return null;
	}

	@Override
	public Stream<UsersSubscription> getSubscriptions() {
		synchronized (this.subs) {
			return this.subs.values().stream();
		}
	}

	@Override
	public Stream<UsersSubscription> getSubscriptionsForPublish() {
		return getSubscriptions();
	}

	public void init(Queue<UsersSubscription> data) {
		UsersSubscription s = null;
		while ((s = data.poll()) != null) {
			subs.put(s.getJid(), s);
		}
	}

	@Override
	public int size() {
		return subs.size();
	}

	@Override
	public String toString() {
		return "NodeSubscriptions: " + subs;
	}

	protected UsersSubscription get(final BareJID bareJid) {
		return this.subs.get(bareJid);
	}
}
