package tigase.pubsub.repository;

import tigase.pubsub.Subscription;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.jid.BareJID;

import java.util.Map;

public interface ISubscriptions {

	public abstract String addSubscriberJid(BareJID jid, Subscription subscription);

	public abstract void changeSubscription(BareJID jid, Subscription subscription);

	public abstract Subscription getSubscription(BareJID jid);

	public abstract String getSubscriptionId(BareJID jid);

	public abstract UsersSubscription[] getSubscriptions();

	public abstract UsersSubscription[] getSubscriptionsForPublish();

	public boolean isChanged();

	public abstract String serialize(Map<BareJID, UsersSubscription> fragment);

}
