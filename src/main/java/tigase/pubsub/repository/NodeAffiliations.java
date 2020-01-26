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

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.xmpp.jid.BareJID;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class NodeAffiliations
		implements IAffiliations {

	protected final static String DELIMITER = ";";
	private static final Logger LOG = Logger.getLogger(NodeAffiliations.class.getName());
	protected final ConcurrentMap<BareJID, UsersAffiliation> affs = new ConcurrentHashMap<BareJID, UsersAffiliation>(16,
																													 0.9f,
																													 8);
	protected NodeAffiliations() {
	}

	protected NodeAffiliations(Map<BareJID, UsersAffiliation> affs) {
		if (affs != null) {
			this.affs.putAll(affs);
		}
	}

	@Override
	public void addAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		affs.put(bareJid, a);
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.log(Level.FINEST, "Added affiliation for {0} as {1}", new Object[]{bareJid, a});
		}
	}

	@Override
	public void changeAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = this.get(bareJid);
		if (a != null) {
			a.setAffiliation(affiliation);
		} else if (affiliation != Affiliation.none) {
			a = new UsersAffiliation(bareJid, affiliation);
			affs.put(bareJid, a);
		}
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.log(Level.FINEST, "Changed affiliation for {0} as {1}",
					new Object[]{bareJid, a});
		}
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		final UsersAffiliation[] a = this.affs.values().toArray(new UsersAffiliation[]{});
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.log(Level.FINEST, "Affiliation for {0} is {1}", new Object[]{Arrays.asList(a)});
		}
		return a;
	}

	public Map<BareJID, UsersAffiliation> getAffiliationsMap() {
		return affs;
	}

	@Override
	public UsersAffiliation getSubscriberAffiliation(BareJID bareJid) {
		UsersAffiliation a = this.get(bareJid);
		if (a == null) {
			a = new UsersAffiliation(bareJid, Affiliation.none);
		}

		LOG.log(Level.FINEST, "Affiliation for {0} is {1}", new Object[]{bareJid, a});

		return a;
	}

	@Override
	public int size() {
		return affs.size();
	}

	@Override
	public String toString() {
		return "NodeAffiliations:" + affs;
	}

	protected UsersAffiliation get(final BareJID bareJid) {
		final UsersAffiliation a = this.affs.get(bareJid);
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.log(Level.FINEST, "Affiliation for {0} is {1}", new Object[]{bareJid, a});
		}
		return a;
	}

}
