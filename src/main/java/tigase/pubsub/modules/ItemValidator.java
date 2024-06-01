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
package tigase.pubsub.modules;

import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Bean(name = "item-validator", parent = PubSubComponent.class, active = false)
public class ItemValidator implements IPubSubRepository.IListener {

	private static final String OMEMO_BUNDLES_NODE_PREFIX = "eu.siacs.conversations.axolotl.bundles:";

	@Inject
	private CachedPubSubRepository cachedPubSubRepository;
	
	@ConfigField(desc = "Max no. of OMEMO identities", alias = "omemo-identities-limit")
	private Integer omemoIdentitiesLimit = null;
	@ConfigField(desc = "Enforce unchangeable OMEMO device identity key", alias = "omemo-unique-device-identity")
	private boolean omemoUniqueDeviceIdentity = false;

	@Override
	public void itemWritten(BareJID serviceJID, String node, String id, String publisher, Element item, String uuid) {

	}

	@Override
	public void itemDeleted(BareJID serviceJID, String node, String id) {

	}

	@Override
	public boolean validateItem(BareJID serviceJID, String node, String id, String publisher, Element item)
			throws PubSubException {
		if (serviceJID.getLocalpart() != null && node != null && node.startsWith(OMEMO_BUNDLES_NODE_PREFIX)) {
			if (omemoIdentitiesLimit != null) {
				try {
					long otherIdentities = Optional.ofNullable(cachedPubSubRepository.getRootCollection(serviceJID))
							.stream()
							.flatMap(Arrays::stream)
							.filter(nodeName -> nodeName.startsWith(OMEMO_BUNDLES_NODE_PREFIX) &&
									!nodeName.equals(node))
							.count();
					if (otherIdentities + 1 > omemoIdentitiesLimit) {
						throw new PubSubException(Authorization.POLICY_VIOLATION, "Exceeded OMEMO bundles limit!");
					}
				} catch (RepositoryException ex) {
					throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "It was not possible to validate the item", ex);
				}
			}
			if (omemoUniqueDeviceIdentity) {
				try {
					String prevIdentityKey = extractIdentityKey(getNodeItem(serviceJID, node, "current"));
					if (prevIdentityKey != null) {
						String newIdentityKey = extractIdentityKey(item);
						if (!Objects.equals(prevIdentityKey, newIdentityKey)) {
							throw new PubSubException(Authorization.POLICY_VIOLATION,
													  "New OMEMO bundle changes identity key!");
						}
					}
				} catch (RepositoryException ex) {
					throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "It was not possible to validate the item", ex);
				}
			}
		}
		return true;
	}

	@Override
	public void serviceRemoved(BareJID serviceJID) {

	}

	protected Element getNodeItem(BareJID serviceJID, String node, String itemId) throws RepositoryException {
		IItems items = cachedPubSubRepository.getNodeItems(serviceJID, node);
		if (items == null) {
			return null;
		}
		IItems.IItem prevItem = items.getItem("current");
		if (prevItem == null) {
			return null;
		}
		return prevItem.getItem();
	}

	protected String extractIdentityKey(Element item) {
		if (item == null) {
			return null;
		}
		Element bundleEL = item.getChild("bundle", "eu.siacs.conversations.axolotl");
		if (bundleEL == null) {
			return null;
		}
		Element identityKey = bundleEL.getChild("identityKey");
		if (identityKey == null) {
			return null;
		}
		return identityKey.getCData();
	}
}
