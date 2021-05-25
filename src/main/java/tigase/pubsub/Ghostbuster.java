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

import tigase.component.ScheduledTask;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.pubsub.repository.PresenceCollectorRepository;
import tigase.server.Packet;
import tigase.server.ReceiverTimeoutHandler;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.JID;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "ghostbuster", parent = PubSubComponent.class, active = true)
public class Ghostbuster extends ScheduledTask {

	private static final Logger log = Logger.getLogger(Ghostbuster.class.getCanonicalName());

	@ConfigField(desc = "")
	private Duration staleTime = Duration.ofHours(1);

	@Inject(bean = "service")
	private PubSubComponent pubSubComponent;
	@Inject
	private PresenceCollectorRepository presenceCollectorRepository;

	public Ghostbuster() {
		super(Duration.ofMinutes(10), Duration.ofMinutes(5));
	}

	@Override
	public void run() {
		try {
			long border = System.currentTimeMillis() - staleTime.toMillis();
			presenceCollectorRepository.expiredUserResourceEntriesStream(border)
					.filter(userResourceEntry -> shouldPing(userResourceEntry.getJid()))
					.sorted(Comparator.comparing(PresenceCollectorRepository.UserResourceEntry::getLastSeen))
					.limit(1000)
					.forEach(this::ping);
		} catch (Throwable e) {
			log.log(Level.WARNING, e, () -> "Problem on executing ghostbuster");
		}
	}

	public void ping(PresenceCollectorRepository.UserResourceEntry entry) {
		final String id = UUID.randomUUID().toString();

		Element ping = new Element("iq", new String[]{"type", "id"},
								   new String[]{"get", id});

		ping.addChild(new Element("ping", new String[]{"xmlns"}, new String[]{"urn:xmpp:ping"}));

		Packet packet = Packet.packetInstance(ping, JID.jidInstanceNS(entry.getServiceJid()), entry.getJid());
		packet.setXMLNS(Packet.CLIENT_XMLNS);

		pubSubComponent.addOutPacketWithTimeout(packet, new ReceiverTimeoutHandler() {
			@Override
			public void timeOutExpired(Packet data) {
				// what should we do here? kick out? or wait a little longer?
			}

			@Override
			public void responseReceived(Packet data, Packet response) {
				// we need to remove this or leave it depending on the result..
				if (response.getType() == StanzaType.error) {
					Authorization errorReason = Optional.ofNullable(response.getElemChild("error"))
							.map(errorEl -> errorEl.findChild(el -> el.getXMLNS() == null ||
									"urn:ietf:params:xml:ns:xmpp-stanzas".equals(el.getXMLNS())))
							.map(Element::getName)
							.map(Authorization::getByCondition)
							.orElse(Authorization.INTERNAL_SERVER_ERROR);
					switch (errorReason) {
						case FEATURE_NOT_IMPLEMENTED:
							// nothing to do, client may not support ping but it answered!
							markAsSeen(entry);
							break;
						default:
							markAsGone(entry, errorReason);
							break;
					}
				} else {
					markAsSeen(entry);
				}
			}
		}, 1, TimeUnit.MINUTES);
	}

	protected void markAsSeen(PresenceCollectorRepository.UserResourceEntry entry) {
		log.log(Level.FINEST, "for " + entry.getServiceJid() + "marking " + entry.getJid() + " as available now");
		entry.markAsSeen();
	}

	protected void markAsGone(PresenceCollectorRepository.UserResourceEntry entry, Authorization reason) {
		log.log(Level.FINEST, () -> "for " + entry.getServiceJid() + "marking " + entry.getJid() + " last seen " +
				entry.getLastSeen() + " as gone due to ping response: " + reason.getCondition());
		presenceCollectorRepository.remove(entry.getServiceJid(), entry.getJid());
	}
	
	protected boolean shouldPing(JID jid) {
		return true;
	}
}
