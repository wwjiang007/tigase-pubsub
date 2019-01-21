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

import tigase.xmpp.BareJID;

import tigase.pubsub.Affiliation;

public class UsersAffiliation implements Cloneable {

	private Affiliation affiliation;

	private final BareJID jid;

	public UsersAffiliation(final BareJID jid) {
		this.affiliation = Affiliation.none;
		this.jid = jid;
	}

	public UsersAffiliation(final BareJID jid, final Affiliation affiliation) {
		this.affiliation = affiliation == null ? Affiliation.none : affiliation;
		this.jid = jid;
	}

	@Override
	public UsersAffiliation clone() throws CloneNotSupportedException {
		UsersAffiliation a = new UsersAffiliation(jid, affiliation);
		return a;
	}

	public Affiliation getAffiliation() {
		return affiliation;
	}

	public BareJID getJid() {
		return jid;
	}

	public void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
	}

	@Override
	public String toString() {
		return "{" + jid + "/" + affiliation + '}';
	}
}
