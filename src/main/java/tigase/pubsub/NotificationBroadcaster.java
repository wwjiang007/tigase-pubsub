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
package tigase.pubsub;

import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.utils.PubSubLogic;
import tigase.pubsub.utils.executors.Executor;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

@Bean(name = "notificationBroadcaster", parent = PubSubComponent.class, active = true)
public class NotificationBroadcaster {

    @Inject
    private PacketWriter packetWriter;
    @Inject
    private PubSubLogic pubSubLogic;
    @Inject(bean = "publishExecutor")
    private Executor publishExecutor;
    
    public void broadcastNotification(Executor.Priority priority, BareJID serviceJID, String nodeName, Element message)
            throws RepositoryException {
        JID senderJid = JID.jidInstance(serviceJID);
        pubSubLogic.subscribersOfNotifications(serviceJID, nodeName).forEach(subscriberJid -> {
            publishExecutor.submit(Executor.Priority.normal, () -> {
                Element clone = message.clone();
                packetWriter.write(Packet.packetInstance(clone, senderJid, subscriberJid));
            });
        });
    }

}
