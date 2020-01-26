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
package tigase.pubsub.repository.cached;

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.jid.BareJID;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

public class NodeSubscriptions
		extends tigase.pubsub.repository.NodeSubscriptions implements ISubscriptionsCached {

	protected final ThreadLocal<Map<BareJID, UsersSubscription>> changedSubs = new ThreadLocal<Map<BareJID, UsersSubscription>>();

	public NodeSubscriptions() {
	}

	public NodeSubscriptions(Map<BareJID, UsersSubscription> subscriptions) {
		super(subscriptions);
	}

	@Override
	public String addSubscriberJid(BareJID bareJid, Subscription subscription) {
		final String subid = Utils.createUID(bareJid);
		UsersSubscription s = new UsersSubscription(bareJid, subid, subscription);

		changedSubs().put(bareJid, s);

		return subid;
	}

	@Override
	public void changeSubscription(BareJID bareJid, Subscription subscription) {
		UsersSubscription s = subs.get(bareJid);

		if (s != null) {
			s.setSubscription(subscription);

			changedSubs().put(s.getJid(), s);
		}
	}

	@Override
	public void changeSubscription(UsersSubscription subscription) {
		changedSubs().put(subscription.getJid(), subscription);
	}

	@Override
	public Map<BareJID, UsersSubscription> getChanged() {
		return Collections.unmodifiableMap(changedSubs());
	}

	@Override
	public Stream<UsersSubscription> getSubscriptions() {
		final Set<UsersSubscription> result = new HashSet<UsersSubscription>();

		result.addAll(this.subs.values());

		result.addAll(this.changedSubs().values());

		return result.stream();
	}

	@Override
	public boolean isChanged() {
		return this.changedSubs().size() > 0;
	}

	public void merge() {
		Map<BareJID, UsersSubscription> changedSubs = changedSubs();
		for (Map.Entry<BareJID, UsersSubscription> entry : changedSubs.entrySet()) {
			if (entry.getValue().getSubscription() == Subscription.none) {
				subs.remove(entry.getKey());
			} else {
				subs.put(entry.getKey(), entry.getValue());
			}
		}
		// subs.putAll(changedSubs);
		changedSubs.clear();
	}

	@Override
	public void resetChangedFlag() {
		changedSubs().clear();
	}

	@Override
	protected UsersSubscription get(final BareJID bareJid) {
		UsersSubscription us = null;

		us = changedSubs().get(bareJid);

		if (us == null) {
			us = subs.get(bareJid);

			if (us != null) {
				try {
					return us.clone();
				} catch (Exception e) {
					log.log(Level.WARNING, "Cloning failed, us: " + us, e);

					return null;
				}
			}
		}

		return us;
	}

	private Map<BareJID, UsersSubscription> changedSubs() {
		Map<BareJID, UsersSubscription> changedSubs = this.changedSubs.get();
		if (changedSubs == null) {
			changedSubs = new HashMap<BareJID, UsersSubscription>();
			this.changedSubs.set(changedSubs);
		}
		return changedSubs;
	}
}
