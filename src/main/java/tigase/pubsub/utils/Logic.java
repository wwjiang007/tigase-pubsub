package tigase.pubsub.utils;

import tigase.component.exceptions.RepositoryException;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Map;

/**
 * Interface of a bean which implements PubSub logic.
 *
 * Created by andrzej on 25.12.2016.
 */
public interface Logic {

	void checkAccessPermission(BareJID serviceJid, String nodeName, JID senderJid)
			throws PubSubException, RepositoryException;

	void checkAccessPermission(BareJID serviceJid, AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations,
							   ISubscriptions nodeSubscriptions, JID senderJid)
			throws PubSubException, RepositoryException;

	boolean hasSenderSubscription(final BareJID bareJid, final IAffiliations affiliations,
								  final ISubscriptions subscriptions) throws RepositoryException;

	boolean isSenderInRosterGroup(BareJID bareJid, AbstractNodeConfig nodeConfig, IAffiliations affiliations,
								  final ISubscriptions subscriptions) throws RepositoryException;

	Element prepareNotificationMessage(JID from, JID to, String id, Element itemToSend, Map<String,String> headers);

}
