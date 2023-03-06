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

import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.pubsub.modules.mam.PubSubQuery;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.mam.MAMRepository;

public interface IExtendedPubSubDAO<T, S extends DataSource, Q extends PubSubQuery> extends IPubSubDAO<T,S,Q> {

	MAMRepository.Item getMAMItem(BareJID ownerJid, T nodeId, String stableId) throws RepositoryException;

	void updateMAMItem(BareJID ownerJid, T nodeId, String stableId, Element message) throws RepositoryException;

}
