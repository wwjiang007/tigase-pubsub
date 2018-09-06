/*
 * AbstractPubSubModule.java
 *
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

package tigase.pubsub;

import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.Module;
import tigase.kernel.beans.Inject;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.Logic;
import tigase.server.Packet;
import tigase.stats.StatisticHolderImpl;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for modules of PubSub component. Provides commonly used properties and methods.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:24:03 GMT
 */
public abstract class AbstractPubSubModule
		extends StatisticHolderImpl
		implements Module {

	protected final static Logger log = Logger.getLogger(AbstractPubSubModule.class.getName());
	@Inject
	protected PubSubConfig config;
	@Inject
	protected Logic logic;
	@Inject
	protected PacketWriter packetWriter;
	@Inject(nullAllowed = false)
	private IPubSubRepository repository;

	public static Element createResultIQ(Element iq) {
		Element e = new Element("iq");
		e.setXMLNS(Packet.CLIENT_XMLNS);
		String id = iq.getAttributeStaticStr("id");
		String from = iq.getAttributeStaticStr("from");
		String to = iq.getAttributeStaticStr("to");

		e.addAttribute("type", "result");
		if (to != null) {
			e.addAttribute("from", to);
		}
		if (from != null) {
			e.addAttribute("to", from);
		}
		if (id != null) {
			e.addAttribute("id", id);
		}

		return e;
	}

	public static List<Element> createResultIQArray(Element iq) {
		return makeArray(createResultIQ(iq));
	}

	@Deprecated
	protected static String findBestJid(final String[] allSubscribers, final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		String best = null;

		for (String j : allSubscribers) {
			if (j.equals(jid)) {
				return j;
			} else {
				if (bareJid.equals(j)) {
					best = j;
				}
			}
		}

		return best;
	}

	public static Collection<BareJID> getActiveSubscribers(final AbstractNodeConfig nodeConfig, final BareJID[] jids,
														   final IAffiliations affiliations,
														   final ISubscriptions subscriptions) {
		Set<BareJID> result = new HashSet<BareJID>();
		final boolean presenceExpired = nodeConfig.isPresenceExpired();

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getActiveSubscribers[2,1] subscriptions: {0}, jids: {1}, presenceExpired: {2}",
					new Object[]{subscriptions, Arrays.asList(jids), presenceExpired});
		}

		if (jids != null) {
			for (BareJID jid : jids) {
				if (presenceExpired) {
				}

				UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(jid);

				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "getActiveSubscribers[2,2] jid: {0}, affiliation: {1}",
							new Object[]{jid, affiliation});
				}

				// /* && affiliation.getAffiliation() != Affiliation.none */
				if (affiliation.getAffiliation() != Affiliation.outcast) {
					Subscription subscription = subscriptions.getSubscription(jid);
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "getActiveSubscribers[2,2] jid: {0}, subscription: {1}}",
								new Object[]{jid, subscription});
					}

					if (subscription == Subscription.subscribed) {
						result.add(jid);
					}
				}
			}
		}

		return result;
	}

	public static Collection<BareJID> getActiveSubscribers(final AbstractNodeConfig nodeConfig,
														   final IAffiliations affiliations,
														   final ISubscriptions subscriptions)
			throws RepositoryException {
		UsersSubscription[] subscribers = subscriptions.getSubscriptionsForPublish();

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "getActiveSubscribers[1] subscriptions: {0}, subscribers: {1}",
					new Object[]{subscriptions, Arrays.asList(subscribers)});
		}

		if (subscribers == null) {
			return Collections.emptyList();
		}

		BareJID[] jids = new BareJID[subscribers.length];

		for (int i = 0; i < subscribers.length; i++) {
			jids[i] = subscribers[i].getJid();
		}

		return getActiveSubscribers(nodeConfig, jids, affiliations, subscriptions);
	}

	public static List<Element> makeArray(Element... elements) {
		LinkedList<Element> result = new LinkedList<Element>();

		for (Element element : elements) {
			result.add(element);
		}

		return result;
	}

	public static List<Packet> makeArray(Packet... packets) {
		LinkedList<Packet> result = new LinkedList<Packet>();

		for (Packet packet : packets) {
			result.add(packet);
		}

		return result;
	}

	public AbstractPubSubModule() {
		this.setStatisticsPrefix(getClass().getSimpleName());
	}

	protected IPubSubRepository getRepository() {
		return repository;
	}

}
