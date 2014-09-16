package tigase.pubsub.modules.ext.usernode;

import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component2.eventbus.EventBus;
import tigase.form.Field;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.modules.SubscribeNodeModule;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.server.Packet;
import tigase.xmpp.BareJID;

public class UserNodeCreator {

	private final PubSubConfig config;

	private LeafNodeConfig defaultNodeConfig;

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	public UserNodeCreator(PubSubConfig config, EventBus eventBus, final LeafNodeConfig defaultNodeConfig) {
		this.config = config;
		this.defaultNodeConfig = defaultNodeConfig;
		eventBus.addHandler(SubscribeNodeModule.NodeSubscribedHandler.NodeSubscribedEvent.TYPE,
				new SubscribeNodeModule.NodeSubscribedHandler() {

					@Override
					public void onNodeSubscribed(Packet packet, String nodeName, BareJID jid, String subId,
							Subscription subscription, Affiliation affiliation) {
						createUserNodeIfRequired(packet.getStanzaTo().getBareJID(), jid);
					}
				});
	}

	public AbstractNodeConfig createUserNodeIfRequired(final BareJID serviceJID, final BareJID subscriberJID) {
		try {
			final String userNodeName = "users/" + subscriberJID;
			AbstractNodeConfig cfg = config.getPubSubRepository().getNodeConfig(serviceJID, userNodeName);
			if (cfg != null) {
				if (log.isLoggable(Level.FINER))
					log.finer("User node exists. Creation skipped.");
				return cfg;
			}

			LeafNodeConfig nodeConfig = new LeafNodeConfig(userNodeName, defaultNodeConfig);
			Field c = nodeConfig.getForm().get("pubsub#persist_items");
			if (c != null) {
				c.setValues(new String[] { "1" });
			}
			c = nodeConfig.getForm().get("pubsub#access_model");
			if (c != null) {
				c.setValues(new String[] { AccessModel.authorize.name() });
			}
			c = nodeConfig.getForm().get("pubsub#max_items");
			if (c != null) {
				c.setValues(new String[] { String.valueOf( Integer.MAX_VALUE) });
			}

			config.getPubSubRepository().createNode(serviceJID, userNodeName, subscriberJID, nodeConfig, NodeType.leaf, "");
			ISubscriptions nodeSubscriptions = config.getPubSubRepository().getNodeSubscriptions(serviceJID, userNodeName);
			IAffiliations nodeaAffiliations = config.getPubSubRepository().getNodeAffiliations(serviceJID, userNodeName);

			nodeSubscriptions.addSubscriberJid(subscriberJID, Subscription.subscribed);
			nodeaAffiliations.addAffiliation(subscriberJID, Affiliation.owner);
			config.getPubSubRepository().update(serviceJID, userNodeName, nodeaAffiliations);
			config.getPubSubRepository().update(serviceJID, userNodeName, nodeSubscriptions);
			config.getPubSubRepository().addToRootCollection(serviceJID, userNodeName);

			return nodeConfig;
		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on create user node.", e);
			return null;
		}

	}

}
