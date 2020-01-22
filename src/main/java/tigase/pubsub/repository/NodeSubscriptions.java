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

import java.util.HashMap;
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

	private boolean changed = false;

	public static tigase.pubsub.repository.cached.NodeSubscriptions create() {
		tigase.pubsub.repository.cached.NodeSubscriptions s = new tigase.pubsub.repository.cached.NodeSubscriptions();

		return s;
	}

	protected NodeSubscriptions() {
	}

	@Override
	public String addSubscriberJid(final BareJID bareJid, final Subscription subscription) {
		final String subid = Utils.createUID(bareJid);
		UsersSubscription s = new UsersSubscription(bareJid, subid, subscription);

		subs.put(bareJid, s);

		changed = true;

		return subid;
	}

	@Override
	public void changeSubscription(BareJID bareJid, Subscription subscription) {
		UsersSubscription s = get(bareJid);

		if (s != null) {
			s.setSubscription(subscription);
			changed = true;
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

	public Map<BareJID, UsersSubscription> getSubscriptionsMap() {
		return subs;
	}

	public void init(Queue<UsersSubscription> data) {
		UsersSubscription s = null;
		while ((s = data.poll()) != null) {
			subs.put(s.getJid(), s);
		}
	}

	@Override
	public boolean isChanged() {
		return changed;
	}

	public void parse(String data) {
		Map<BareJID, UsersSubscription> parsed = new HashMap<BareJID, UsersSubscription>();
		String[] tokens = data.split(DELIMITER);
		int c = 0;
		BareJID jid = null;
		String subid = null;
		String state = null;

		for (String t : tokens) {
			if (c == 2) {
				state = t;
				++c;
			} else {
				if (c == 1) {
					subid = t;
					++c;
				} else {
					if (c == 0) {
						jid = BareJID.bareJIDInstanceNS(t);
						++c;
					}
				}
			}

			if (c == 3) {
				UsersSubscription b = new UsersSubscription(jid, subid, Subscription.valueOf(state));

				subs.put(jid, b);
				jid = null;
				subid = null;
				state = null;
				c = 0;
			}
		}
	}

	public void replaceBy(final ISubscriptions nodeSubscriptions) {
		synchronized (this.subs) {
			if (nodeSubscriptions instanceof NodeSubscriptions) {
				NodeSubscriptions ns = (NodeSubscriptions) nodeSubscriptions;

				this.changed = true;
				subs.clear();

				for (UsersSubscription a : ns.subs.values()) {
					subs.put(a.getJid(), a);
				}
			} else {
				throw new RuntimeException("!!!!!!!!!!!!!!!!!!!" + nodeSubscriptions.getClass());
			}
		}
	}

	public void resetChangedFlag() {
		this.changed = false;
	}

	@Override
	public String serialize(Map<BareJID, UsersSubscription> fragment) {
		StringBuilder sb = new StringBuilder();

		for (UsersSubscription s : fragment.values()) {
			if (s.getSubscription() != Subscription.none) {
				sb.append(s.getJid());
				sb.append(DELIMITER);
				sb.append(s.getSubid());
				sb.append(DELIMITER);
				sb.append(s.getSubscription().name());
				sb.append(DELIMITER);
			}
		}

		return sb.toString();
	}

	@Override
	public String toString() {
		return "NodeSubscriptions: " + subs;
	}

	protected UsersSubscription get(final BareJID bareJid) {
		return this.subs.get(bareJid);
	}
}
