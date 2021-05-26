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
package tigase.pubsub.repository;

import tigase.kernel.beans.Bean;
import tigase.kernel.beans.config.ConfigField;
import tigase.kernel.beans.selector.ClusterModeRequired;
import tigase.pubsub.PubSubComponent;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ClusterModeRequired(active = false)
@Bean(name = "presenceRepository", parent = PubSubComponent.class, active = true)
public class PresenceCollectorRepository {
	
	protected final ConcurrentMap<BareJID, ServiceEntry> entriesByService = new ConcurrentHashMap<>();

	@ConfigField(desc = "Maximum amount of last available user resources kept in cache")
	private int maximumNoOfResources = 20;

	private final Object[] JID_LOCKS;

	public PresenceCollectorRepository() {
		JID_LOCKS = new Object[Runtime.getRuntime().availableProcessors() * 4];
		for (int i=0; i<JID_LOCKS.length; i++) {
			JID_LOCKS[i] = new Object();
		}
	}

	public String add(BareJID serviceJid, JID jid, String caps) {
		if (jid.getResource() != null) {
			return entriesByService.computeIfAbsent(serviceJid, ServiceEntry::new).add(jid, caps);
		}
		return null;
	}

	public Stream<JID> getAllAvailableJids(final BareJID serviceJid, Predicate<String> nodesPredicate) {
		ServiceEntry entriesByUser = entriesByService.get(serviceJid);
		if (entriesByUser == null) {
			return Stream.empty();
		}
		Stream<UserResourceEntry> resultStream = entriesByUser.userEntriesStream().flatMap(userEntry -> userEntry.userResourceEntriesStream());
		if (nodesPredicate != null) {
			resultStream = resultStream.filter(entry -> entry.containsCapsNode(nodesPredicate));
		}
		return resultStream.map(UserResourceEntry::getJid);
	}

	public List<JID> getAllAvailableResources(final BareJID serviceJid, final BareJID bareJid) {
		ServiceEntry entriesByUser = entriesByService.get(serviceJid);
		if (entriesByUser == null) {
			return Collections.emptyList();
		}

		UserEntry values = entriesByUser.get(bareJid);
		if (values == null) {
			return Collections.emptyList();
		}
		return values.userResourceEntriesStream().map(UserResourceEntry::getJid).collect(Collectors.toList());
	}

	public boolean isAvailable(final BareJID serviceJid, final BareJID bareJid) {
		ServiceEntry entriesByUser = entriesByService.get(serviceJid);
		if (entriesByUser == null) {
			return false;
		}
		
		final UserEntry resources = entriesByUser.get(bareJid);

		return (resources != null) && (resources.size() > 0);
	}

	public boolean remove(final BareJID serviceJid, final JID jid) {
		ServiceEntry entriesByUser = entriesByService.get(serviceJid);
		if (entriesByUser == null) {
			return false;
		}

		return entriesByUser.remove(jid);
	}

	public Collection<ServiceEntry> getServiceEntries() {
		return entriesByService.values();
	}

	public Stream<UserResourceEntry> userResourceEntryStream() {
		return entriesByService.values()
				.stream()
				.flatMap(ServiceEntry::userEntriesStream)
				.flatMap(UserEntry::userResourceEntriesStream);
	}

	public Stream<UserResourceEntry> expiredUserResourceEntriesStream(long expirationTimestamp) {
		return userResourceEntryStream().filter(
				userResourceEntry -> userResourceEntry.isOlderThan(expirationTimestamp));
	}

	public class ServiceEntry {
		private final BareJID serviceJid;
		private final ConcurrentHashMap<BareJID, UserEntry> usersEntries = new ConcurrentHashMap<>();

		public ServiceEntry(BareJID serviceJid) {
			this.serviceJid = serviceJid;
		}

		public String add(JID jid, String caps) {
			return synchronizeOnUserJID(jid.getBareJID(), () -> usersEntries.computeIfAbsent(jid.getBareJID(), k -> new UserEntry(serviceJid, k))
					.add(jid.getResource(), caps));
		}

		public boolean remove(JID jid) {
			return synchronizeOnUserJID(jid.getBareJID(), () -> {
				UserEntry entries = usersEntries.get(jid.getBareJID());
				if (entries == null) {
					return false;
				}
				boolean result = entries.remove(jid.getResource());
				if (entries.isEmpty()) {
					usersEntries.remove(jid.getBareJID());
				}
				return result;
			});
		}

		public UserEntry get(BareJID jid) {
			return usersEntries.get(jid);
		}

		public Collection<UserEntry> getUserEntries() {
			return usersEntries.values();
		}

		public Stream<UserEntry> userEntriesStream() {
			return usersEntries.values().stream();
		}

		protected <T> T synchronizeOnUserJID(BareJID jid, Supplier<T> run) {
			synchronized (JID_LOCKS[Math.abs(jid.hashCode()) % JID_LOCKS.length]) {
				return run.get();
			}
		}
	}

	public class UserEntry {

		private final BareJID serviceJid;
		private final BareJID jid;
		private final CopyOnWriteArrayList<UserResourceEntry> entries = new CopyOnWriteArrayList<>();

		public UserEntry(BareJID serviceJid, BareJID jid)  {
			this.serviceJid = serviceJid;
			this.jid = jid;
		}

		public BareJID getJid() {
			return jid;
		}

		public BareJID getServiceJid() {
			return serviceJid;
		}

		public synchronized String add(String resource, String caps) {
			String oldCaps = null;
			for (int i=0; i<entries.size(); i++) {
				UserResourceEntry e = entries.get(i);
				if (e.matches(resource)) {
					oldCaps = entries.remove(i).caps;
					break;
				}
			}
			// limit number of kept last available resources
			while (entries.size() >= maximumNoOfResources) {
				// we are doing this in a synchronized block, so we are adding only one resource at once
				entries.remove(0);
			}
			entries.add(new UserResourceEntry(this, resource, caps == null ? null : caps.intern()));
			return oldCaps;
		}

		public synchronized boolean remove(String resource) {
			for (int i=0; i<entries.size(); i++) {
				if (entries.get(i).matches(resource)) {
					entries.remove(i);
					return true;
				}
			}
			return false;
		}

		public synchronized void markAsSeen(UserResourceEntry entry) {
			remove(entry.resource);
			entries.add(entry);
		}

		public List<String> getResources() {
			List<String> result = new ArrayList<>(entries.size());
			for (UserResourceEntry e : entries) {
				result.add(e.resource);
			}
			return result;
		}

		public <T> List<T> mapEntries(Function<UserResourceEntry,T> function, Predicate<UserResourceEntry> filter) {
			List<T> result = new ArrayList<>();
			for (UserResourceEntry e : entries) {
				if (!filter.test(e)) {
					continue;
				}
				result.add(function.apply(e));
			}
			return result;
		}

		public Stream<UserResourceEntry> getEntriesOlderThen(long timestamp) {
			return entries.stream().filter(e -> e.isOlderThan(timestamp));
		}

		public boolean isEmpty() {
			return entries.isEmpty();
		}

		public int size() {
			return entries.size();
		}

		public Stream<UserResourceEntry> userResourceEntriesStream() {
			return entries.stream();
		}
	}

	public class UserResourceEntry {
		private final UserEntry entries;
		private final String resource;
		private final String caps;
		private long lastSeen = System.currentTimeMillis();

		public UserResourceEntry(UserEntry entries, String resource, String caps) {
			this.entries = entries;
			this.resource = resource;
			this.caps = caps;
		}

		public BareJID getServiceJid() {
			return entries.getServiceJid();
		}

		public JID getJid() {
			return JID.jidInstanceNS(entries.getJid(), resource);
		}

		public String getResource() {
			return resource;
		}

		public boolean containsCapsNode(Predicate<String> predicate) {
			return caps != null && predicate.test(caps);
		}

		public String getCaps() {
			return caps;
		}

		public long getLastSeen() {
			return lastSeen;
		}

		protected boolean matches(String resource) {
			if (resource == null) {
				return this.resource == null;
			} else {
				return resource.equals(this.resource);
			}
		}

		public boolean isOlderThan(long timestamp) {
			return lastSeen < timestamp;
		}

		public void markAsSeen() {
			lastSeen = System.currentTimeMillis();
			entries.markAsSeen(this);
		}
	}
}
