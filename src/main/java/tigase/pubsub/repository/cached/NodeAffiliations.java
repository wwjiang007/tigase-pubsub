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

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.xmpp.jid.BareJID;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NodeAffiliations
		extends tigase.pubsub.repository.NodeAffiliations implements IAffiliationsCached {

	private static final Logger log = Logger.getLogger(NodeAffiliations.class.getName());

	protected final ThreadLocal<Map<BareJID, UsersAffiliation>> changedAffs = new ThreadLocal<Map<BareJID, UsersAffiliation>>();

	public NodeAffiliations() {
	}

	public NodeAffiliations(Map<BareJID, UsersAffiliation> affs) {
		super(affs);
	}
		
	@Override
	public void addAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		changedAffs().put(bareJid, a);
	}

	@Override
	public void changeAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = this.get(bareJid);
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
		if (a != null) {
			a.setAffiliation(affiliation);
			changedAffs.put(bareJid, a);
		} else if (affiliation != Affiliation.none) {
			a = new UsersAffiliation(bareJid, affiliation);
			changedAffs.put(bareJid, a);
		}
	}

	@Override
	public NodeAffiliations clone() throws CloneNotSupportedException {
		NodeAffiliations clone = new NodeAffiliations();
		for (UsersAffiliation a : this.affs.values()) {
			clone.affs.put(a.getJid(), a.clone());
		}
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
		Map<BareJID, UsersAffiliation> cloneChangedAffs = clone.changedAffs();
		for (UsersAffiliation a : changedAffs.values()) {
			cloneChangedAffs.put(a.getJid(), a.clone());
		}
		return clone;
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		final Set<UsersAffiliation> result = new HashSet<UsersAffiliation>();
		result.addAll(this.affs.values());
		result.addAll(this.changedAffs().values());
		return result.toArray(new UsersAffiliation[]{});
	}

	public Map<BareJID, UsersAffiliation> getChanged() {
		return Collections.unmodifiableMap(changedAffs());
	}
	
	@Override
	public boolean isChanged() {
		return changedAffs().size() > 0;
	}

	@Override
	public void merge() {
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
		for (Map.Entry<BareJID, UsersAffiliation> entry : changedAffs.entrySet()) {
			if (entry.getValue().getAffiliation() == Affiliation.none) {
				affs.remove(entry.getKey());
			} else {
				affs.put(entry.getKey(), entry.getValue());
			}
		}
		changedAffs.clear();

	}

	@Override
	public void resetChangedFlag() {
		changedAffs().clear();
	}

	@Override
	protected UsersAffiliation get(BareJID bareJid) {
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
		UsersAffiliation us = changedAffs.get(bareJid);
		if (us == null) {
			us = affs.get(bareJid);
			if (us != null) {
				try {
					return us.clone();
				} catch (Exception e) {
					log.log(Level.WARNING, "Cloning failed", e);
					return null;
				}
			}
		}
		return us;
	}

	private Map<BareJID, UsersAffiliation> changedAffs() {
		Map<BareJID, UsersAffiliation> changedAffs = this.changedAffs.get();

		if (changedAffs == null) {
			changedAffs = new HashMap<BareJID, UsersAffiliation>();
			this.changedAffs.set(changedAffs);
		}

		return changedAffs;
	}
}
