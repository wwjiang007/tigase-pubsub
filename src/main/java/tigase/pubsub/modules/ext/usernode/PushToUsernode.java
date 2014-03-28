package tigase.pubsub.modules.ext.usernode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component2.eventbus.EventBus;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.util.DateTimeFormatter;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

public class PushToUsernode {

	private final PubSubConfig config;

	private final DateTimeFormatter dtf = new DateTimeFormatter();

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	private final PublishItemModule publishItemModule;

	public PushToUsernode(PubSubConfig config, EventBus eventBus, PublishItemModule publishItemModule) {
		this.publishItemModule = publishItemModule;
		this.config = config;
		eventBus.addHandler(PublishItemModule.ItemPublishedHandler.ItemPublishedEvent.TYPE,
				new PublishItemModule.ItemPublishedHandler() {

					@Override
					public void onItemPublished(BareJID serviceJID, String node, Collection<Element> items) {
						if (!node.startsWith("users/"))
							onItemsPublish(serviceJID, node, items);
					}
				});
	}

	protected void onItemsPublish(BareJID serviceJID, String nodeName, Collection<Element> items) {
		try {
			final ISubscriptions nodeSubscriptions = config.getPubSubRepository().getNodeSubscriptions(serviceJID, nodeName);

			final List<Element> itemsToSend = new ArrayList<Element>();
			for (Element element : items) {
				Element it = new Element("item");
				it.setAttribute("id", UUID.randomUUID().toString());

				Element unread = new Element("unread");
				unread.setAttribute("node", nodeName);
				unread.setAttribute("id", element.getAttributeStaticStr("id"));
				unread.setAttribute("timestamp", dtf.formatDateTime(new Date()));
				it.addChild(unread);

				itemsToSend.add(it);
			}

			for (UsersSubscription subscription : nodeSubscriptions.getSubscriptions()) {
				final String destNodeName = "users/" + subscription.getJid();
				final LeafNodeConfig destNodeConfig = (LeafNodeConfig) config.getPubSubRepository().getNodeConfig(serviceJID,
						destNodeName);
				final IAffiliations destNodeAffiliations = config.getPubSubRepository().getNodeAffiliations(serviceJID,
						nodeName);
				final ISubscriptions destNodeSubscriptions = config.getPubSubRepository().getNodeSubscriptions(serviceJID,
						nodeName);

				publishItemModule.doPublishItems(serviceJID, destNodeName, destNodeConfig, destNodeAffiliations,
						destNodeSubscriptions, nodeName, itemsToSend);
			}
		} catch (RepositoryException e) {
			log.log(Level.WARNING, "Can't publish to usernodes", e);
		}
	}

}
