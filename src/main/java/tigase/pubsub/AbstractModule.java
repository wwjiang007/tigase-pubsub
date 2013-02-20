/*
 * AbstractModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.pubsub;

//~--- non-JDK imports --------------------------------------------------------

import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;

import tigase.util.JIDUtils;

import tigase.xml.Element;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Class description
 *
 *
 * @version 5.0.0, 2010.03.27 at 05:24:03 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public abstract class AbstractModule
				implements Module {
	// ~--- methods
	// --------------------------------------------------------------

	/** Field description */
	protected Logger log = Logger.getLogger(this.getClass().getName());

	// ~--- constructors
	// ---------------------------------------------------------

	/** Field description */
	protected final PubSubConfig config;

	/** Field description */
	protected final IPubSubRepository repository;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param pubsubRepository
	 */
	public AbstractModule(final PubSubConfig config,
												final IPubSubRepository pubsubRepository) {
		this.config     = config;
		this.repository = pubsubRepository;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param iq
	 *
	 * @return
	 */
	public static Element createResultIQ(Element iq) {
		Element e   = new Element("iq");
		String id   = iq.getAttributeStaticStr("id");
		String from = iq.getAttributeStaticStr("from");
		String to   = iq.getAttributeStaticStr("to");

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

	/**
	 * Method description
	 *
	 *
	 * @param iq
	 *
	 * @return
	 */
	public static List<Element> createResultIQArray(Element iq) {
		return makeArray(createResultIQ(iq));
	}

	/**
	 * Method description
	 *
	 *
	 * @param elements
	 *
	 * @return
	 */
	public static List<Element> makeArray(Element... elements) {
		LinkedList<Element> result = new LinkedList<Element>();

		for (Element element : elements) {
			result.add(element);
		}

		return result;
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param allSubscribers
	 * @param jid
	 *
	 * @return
	 */
	protected String findBestJid(final String[] allSubscribers, final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		String best          = null;

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

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param nodeConfig
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public List<String> getActiveSubscribers(final AbstractNodeConfig nodeConfig,
					final IAffiliations affiliations, final ISubscriptions subscriptions)
					throws RepositoryException {
		UsersSubscription[] subscribers = subscriptions.getSubscriptions();

		if (subscribers == null) {
			return Collections.emptyList();
		}

		String[] jids = new String[subscribers.length];

		for (int i = 0; i < subscribers.length; i++) {
			jids[i] = subscribers[i].getJid().toString();
		}

		return getActiveSubscribers(nodeConfig, jids, affiliations, subscriptions);
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param nodeConfig
	 * @param jids
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 */
	public List<String> getActiveSubscribers(final AbstractNodeConfig nodeConfig,
					final String[] jids, final IAffiliations affiliations,
					final ISubscriptions subscriptions) {
		List<String> result           = new ArrayList<String>();
		final boolean presenceExpired = nodeConfig.isPresenceExpired();

		if (jids != null) {
			for (String jid : jids) {
				if (presenceExpired) {}

				UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(jid);

				// /* && affiliation.getAffiliation() != Affiliation.none */
				if (affiliation.getAffiliation() != Affiliation.outcast) {
					Subscription subscription = subscriptions.getSubscription(jid);

					if (subscription == Subscription.subscribed) {
						result.add(jid);
					}
				}
			}
		}

		return result;
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	protected boolean hasSenderSubscription(final String jid,
					final IAffiliations affiliations, final ISubscriptions subscriptions)
					throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		final String bareJid                  = JIDUtils.getNodeID(jid);

		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation =
				affiliations.getSubscriberAffiliation(owner.getJid().toString());

			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}
			if (bareJid.equals(owner)) {
				return true;
			}

			String[] buddies = this.repository.getUserRoster(owner.getJid());

			for (String buddy : buddies) {
				if (bareJid.equals(buddy)) {
					String s = this.repository.getBuddySubscription(owner.getJid(), bareJid);

					if ((s != null) && ("from".equals(s) || "both".equals(s))) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 * @param nodeConfig
	 * @param affiliations
	 * @param subscriptions
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	protected boolean isSenderInRosterGroup(String jid, AbstractNodeConfig nodeConfig,
					IAffiliations affiliations, final ISubscriptions subscriptions)
					throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		final String bareJid                  = JIDUtils.getNodeID(jid);
		final String[] groupsAllowed          = nodeConfig.getRosterGroupsAllowed();

		if ((groupsAllowed == null) || (groupsAllowed.length == 0)) {
			return true;
		}
		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation =
				affiliations.getSubscriberAffiliation(owner.getJid().toString());

			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}
			if (bareJid.equals(owner)) {
				return true;
			}

			String[] buddies = this.repository.getUserRoster(owner.getJid());

			for (String buddy : buddies) {
				if (bareJid.equals(buddy)) {
					String[] groups = this.repository.getBuddyGroups(owner.getJid(), bareJid);

					for (String group : groups) {
						if (Utils.contain(group, groupsAllowed)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}
}



// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com


//~ Formatted in Tigase Code Convention on 13/02/20
