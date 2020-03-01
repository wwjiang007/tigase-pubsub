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
package tigase.pubsub.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;

import java.util.Map;
import java.util.logging.Level;

@Bean(name = "retrieveAffiliationsModule", parent = PubSubComponent.class, active = true)
public class RetrieveAffiliationsModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub"))
			.add(ElementCriteria.name("affiliations"));

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#retrieve-affiliations",
							"http://jabber.org/protocol/pubsub#publisher-affiliation",
							"http://jabber.org/protocol/pubsub#outcast-affiliation",
							"http://jabber.org/protocol/pubsub#member-affiliation"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			final BareJID serviceJid = packet.getStanzaTo().getBareJID();
			final Element pubsub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element affiliations = pubsub.getChild("affiliations");
			final BareJID senderBareJid = packet.getStanzaFrom().getBareJID();
			final Element pubsubResult = new Element("pubsub", new String[]{"xmlns"},
													 new String[]{"http://jabber.org/protocol/pubsub"});

			final Packet result = packet.okResult(pubsubResult, 0);

			final Element affiliationsResult = new Element("affiliations");

			pubsubResult.addChild(affiliationsResult);

			Map<String, UsersAffiliation> userAffiliations = getRepository().getUserAffiliations(serviceJid, senderBareJid);
			for (Map.Entry<String, UsersAffiliation> entry : userAffiliations.entrySet()) {
				Affiliation affiliation = entry.getValue().getAffiliation();
				Element a = new Element("affiliation", new String[]{"node", "affiliation"},
										new String[]{entry.getKey(), affiliation.name()});

				affiliationsResult.addChild(a);
			}

			packetWriter.write(result);
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing retrieve affiliation packet", e);
			throw new RuntimeException(e);
		}
	}
}
