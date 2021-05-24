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
import tigase.kernel.beans.selector.ClusterModeRequired;
import tigase.pubsub.PubSubComponent;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ClusterModeRequired(active = false)
@Bean(name = "presenceRepository", parent = PubSubComponent.class, active = true)
public class PresenceCollectorRepository {
	
	protected final ConcurrentMap<BareJID, ConcurrentMap<BareJID, Entries>> presenceByService = new ConcurrentHashMap<>();

	public String add(BareJID serviceJid, JID jid, String caps) {
		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();

		ConcurrentMap<BareJID, Entries> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			ConcurrentMap<BareJID, Entries> tmp = new ConcurrentHashMap<>();
			presenceByUser = presenceByService.putIfAbsent(serviceJid, tmp);
			if (presenceByUser == null) {
				presenceByUser = tmp;
			}
		}

		if (resource != null) {
			return presenceByUser.computeIfAbsent(bareJid, (k) -> new Entries()).add(resource, caps);
		}
		return null;
	}

	public Stream<JID> getAllAvailableJids(final BareJID serviceJid, Predicate<String> nodesPredicate) {
		ConcurrentMap<BareJID, Entries> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return Stream.empty();
		}
		return presenceByUser.entrySet().stream().flatMap(entry -> {
			BareJID bareJID = entry.getKey();
			List<String> resources;
			Entries values = entry.getValue();
			if (nodesPredicate != null) {
				resources = values.mapEntries(Entry::getResource, e -> e.containsCapsNode(nodesPredicate));
			} else {
				resources = values.getResources();
			}
			return resources.stream().map(res -> JID.jidInstanceNS(bareJID, res));
		});
	}

	public List<JID> getAllAvailableResources(final BareJID serviceJid, final BareJID bareJid) {
		ConcurrentMap<BareJID, Entries> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return Collections.emptyList();
		}
		Entries values = presenceByUser.get(bareJid);
		if (values == null) {
			return Collections.emptyList();
		}
		return values.getResources().stream().map(res -> JID.jidInstanceNS(bareJid, res)).collect(Collectors.toList());
	}

	public boolean isAvailable(final BareJID serviceJid, final BareJID bareJid) {
		ConcurrentMap<BareJID, Entries> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return false;
		}
		final Entries resources = presenceByUser.get(bareJid);

		return (resources != null) && (resources.size() > 0);
	}

	public boolean remove(final BareJID serviceJid, final JID jid) {
		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();

		ConcurrentMap<BareJID, Entries> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return false;
		}

		Entries resources = presenceByUser.get(bareJid);

		if (resources != null) {
			return resources.remove(resource);
		}

		return false;
	}

	public class Entries {

		private ArrayList<Entry> entries = new ArrayList<>();

		public Entries()  {

		}

		public synchronized String add(String resource, String caps) {
			String oldCaps = null;
			for (int i=0; i<entries.size(); i++) {
				Entry e = entries.get(i);
				if (e.matches(resource)) {
					oldCaps = entries.remove(i).caps;
					break;
				}
			}
			entries.add(new Entry(resource, caps == null ? null : caps.intern()));
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

		public synchronized List<String> getResources() {
			List<String> result = new ArrayList<>(entries.size());
			for (Entry e : entries) {
				result.add(e.resource);
			}
			return result;
		}

		public synchronized <T> List<T> mapEntries(Function<Entry,T> function, Predicate<Entry> filter) {
			List<T> result = new ArrayList<>();
			for (Entry e : entries) {
				if (!filter.test(e)) {
					continue;
				}
				result.add(function.apply(e));
			}
			return result;
		}

		public synchronized int size() {
			return entries.size();
		}

	}

	public class Entry {
		private final String resource;
		private final String caps;

		public Entry(String resource, String caps) {
			this.resource = resource;
			this.caps = caps;
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

		protected boolean matches(String resource) {
			if (resource == null) {
				return this.resource == null;
			} else {
				return resource.equals(this.resource);
			}
		}
	}
}
