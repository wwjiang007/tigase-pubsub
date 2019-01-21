/**
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
package tigase.pubsub.repository.stateless;

import tigase.pubsub.Subscription;
import tigase.xmpp.jid.BareJID;

/**
 * Implementation of single subscription entry.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:23:47 GMT
 */
public class UsersSubscription
		implements Cloneable {

	private final BareJID jid;
	private final String subid;
	private Subscription subscription;

	public UsersSubscription(BareJID jid, String subid, Subscription subscriptionType) {
		super();
		this.jid = jid;
		this.subid = subid;
		this.subscription = subscriptionType;
	}

	@Override
	public UsersSubscription clone() throws CloneNotSupportedException {
		UsersSubscription a = new UsersSubscription(jid, subid, subscription);

		return a;
	}

	public BareJID getJid() {
		return jid;
	}

	public String getSubid() {
		return subid;
	}

	public Subscription getSubscription() {
		return subscription;
	}

	public void setSubscription(Subscription subscriptionType) {
		this.subscription = subscriptionType;
	}

	@Override
	public String toString() {
		return "{" + jid + "/" + subscription + '}';
	}

}
