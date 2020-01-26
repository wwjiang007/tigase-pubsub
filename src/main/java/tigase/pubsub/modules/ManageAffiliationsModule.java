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

import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@Bean(name = "manageAffiliationsModule", parent = PubSubComponent.class, active = true)
public class ManageAffiliationsModule
		extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.name("iq")
			.add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner"))
			.add(ElementCriteria.name("affiliations"));

	private static Packet createAffiliationNotification(JID fromJid, JID toJid, String nodeName,
														Affiliation affilation) {
		Packet message = Message.getMessage(fromJid, toJid, null, null, null, null, null);
		Element pubsub = new Element("pubsub", new String[]{"xmlns"},
									 new String[]{"http://jabber.org/protocol/pubsub"});

		message.getElement().addChild(pubsub);

		Element affilations = new Element("affiliations", new String[]{"node"}, new String[]{nodeName});

		pubsub.addChild(affilations);
		affilations.addChild(new Element("affilation", new String[]{"jid", "affiliation"},
										 new String[]{toJid.toString(), affilation.name()}));

		return message;
	}

	@Override
	public String[] getFeatures() {
		return new String[]{"http://jabber.org/protocol/pubsub#modify-affiliations"};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			BareJID toJid = packet.getStanzaTo().getBareJID();
			Element element = packet.getElement();
			Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			Element affiliations = pubsub.getChild("affiliations");
			String nodeName = affiliations.getAttributeStaticStr("node");
			StanzaType type = packet.getType();

			if ((type == null) || (type != StanzaType.get && type != StanzaType.set)) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}

			JID senderJid = packet.getStanzaFrom();
			pubSubLogic.checkPermission(toJid, nodeName, senderJid, PubSubLogic.Action.manageNode);

			final IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(toJid, nodeName);

			if (type == StanzaType.get) {
				processGet(packet, affiliations, nodeName, nodeAffiliations, packetWriter);
			} else if (type == StanzaType.set) {
				processSet(packet, affiliations, nodeName, nodeAffiliations, packetWriter);
			}

		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing affiliation packet", e);

			throw new RuntimeException(e);
		}
	}

	private void processGet(Packet packet, Element affiliations, String nodeName, final IAffiliations nodeAffiliations,
							PacketWriter packetWriter) throws RepositoryException {
		Element ps = new Element("pubsub", new String[]{"xmlns"},
								 new String[]{"http://jabber.org/protocol/pubsub#owner"});

		Packet iq = packet.okResult(ps, 0);

		Element afr = new Element("affiliations", new String[]{"node"}, new String[]{nodeName});

		ps.addChild(afr);

		UsersAffiliation[] affiliationsList = nodeAffiliations.getAffiliations();

		if (log.isLoggable(Level.FINEST)) {
			log.finest("Node affiliations: " + nodeName + " / " + Arrays.toString(affiliationsList));
		}

		if (affiliationsList != null) {
			for (UsersAffiliation affi : affiliationsList) {
				if (affi.getAffiliation() == Affiliation.none) {
					continue;
				}

				Element affiliation = new Element("affiliation", new String[]{"jid", "affiliation"},
												  new String[]{affi.getJid().toString(), affi.getAffiliation().name()});

				afr.addChild(affiliation);
			}
		}

		getRepository().update(packet.getStanzaTo().getBareJID(), nodeName, nodeAffiliations);

		packetWriter.write(iq);
	}

	private void processSet(final Packet packet, final Element affiliations, final String nodeName, final IAffiliations nodeAffiliations,
							PacketWriter packetWriter) throws PubSubException, RepositoryException {
		final AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(packet.getStanzaTo().getBareJID(), nodeName);
		List<Element> affs = affiliations.getChildren();

		for (Element a : affs) {
			if (!"affiliation".equals(a.getName())) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
		}

		Map<JID, Affiliation> changedAffiliations = new HashMap<JID, Affiliation>();

		for (Element af : affs) {
			String strAfiliation = af.getAttributeStaticStr("affiliation");
			String jidStr = af.getAttributeStaticStr("jid");
			JID jid = JID.jidInstanceNS(jidStr);

			if (strAfiliation == null) {
				continue;
			}

			Affiliation newAffiliation = Affiliation.valueOf(strAfiliation);
			Affiliation oldAffiliation = nodeAffiliations.getSubscriberAffiliation(jid.getBareJID()).getAffiliation();

			oldAffiliation = (oldAffiliation == null) ? Affiliation.none : oldAffiliation;
			if ((oldAffiliation == Affiliation.none) && (newAffiliation != Affiliation.none)) {
				nodeAffiliations.addAffiliation(jid.getBareJID(), newAffiliation);
				changedAffiliations.put(jid, newAffiliation);
			} else {
				nodeAffiliations.changeAffiliation(jid.getBareJID(), newAffiliation);
				changedAffiliations.put(jid, newAffiliation);
			}
		}

		getRepository().update(packet.getStanzaTo().getBareJID(), nodeName, nodeAffiliations);

		for (Map.Entry<JID, Affiliation> entry : changedAffiliations.entrySet()) {
			if (nodeConfig.isTigaseNotifyChangeSubscriptionAffiliationState()) {
				packetWriter.write(createAffiliationNotification(packet.getStanzaTo(), entry.getKey(), nodeName,
																 entry.getValue()));
			}
		}

		Packet iq = packet.okResult((Element) null, 0);
		packetWriter.write(iq);
	}
}
