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
package tigase.pubsub.modules.mam;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.IPubSubConfig;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by andrzej on 22.12.2016.
 */
@Bean(name = "mamQueryParser", parent = PubSubComponent.class, active = true)
public class MAMQueryParser
		extends tigase.xmpp.mam.MAMQueryParser<Query> {
	private static final String MAM2_XMLNS = "urn:xmpp:mam:2";

	protected static final Set<String> XMLNSs = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList(MAM_XMLNS, MAM2_XMLNS)));

	@Inject
	private IPubSubRepository pubSubRepository;

	@Inject
	private PubSubLogic pubSubLogic;

	@Inject
	private IPubSubConfig pubSubConfig;

	private final boolean nullNodeAllowed;

	public MAMQueryParser() {
		this(false);
	}

	protected MAMQueryParser(boolean nullNodeAllowed) {
		this.nullNodeAllowed = nullNodeAllowed;
	}

	@Override
	public Set<String> getXMLNSs() {
		return XMLNSs;
	}

	@Override
	public Query parseQuery(Query query, Packet packet) throws ComponentException {
		String node = packet.getAttributeStaticStr(Iq.IQ_QUERY_PATH, "node");
		validateNode(packet.getStanzaTo().getBareJID(), node);
		query.setPubsubNode(node);

		super.parseQuery(query, packet);

		return query;
	}

	protected void validateNode(BareJID serviceJID, String node) throws PubSubException {
		if (node == null) {
			throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
		}
		try {
			if (!pubSubLogic.isMAMEnabled(serviceJID, node)) {
				throw new PubSubException(Authorization.NOT_ALLOWED);
			}
		} catch (RepositoryException ex) {
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}
}
