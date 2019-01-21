/**
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
package tigase.pubsub.repository.converter;

import tigase.db.converter.Converter;
import tigase.db.converter.Convertible;
import tigase.db.converter.QueryExecutor;
import tigase.kernel.beans.Inject;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.Subscription;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.INodeMeta;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.jid.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PubSubNodeConverter
		implements Convertible<PubSubNodeEntity> {

	private final static Logger log = Logger.getLogger(PubSubNodeConverter.class.getName());
	@Inject
	QueryExecutor queryExecutor;
	private Converter.ConverterProperties properties;
	private PubSubQueries queries;
	@Inject(nullAllowed = false)
	private IPubSubRepository repository;

	/**
	 * Specification from ejabberd sources:
	 *
	 * -spec decode_affiliation(Arg :: binary()) -> atom().
	 * decode_affiliation(<<"o">>) -> owner;
	 * decode_affiliation(<<"p">>) -> publisher;
	 * decode_affiliation(<<"u">>) -> publish_only;
	 * decode_affiliation(<<"m">>) -> member;
	 * decode_affiliation(<<"c">>) -> outcast;
	 * decode_affiliation(_) -> none.
	 *
	 * @param affiliation string to be checked
	 *
	 * @return {@link Affiliation} representation of string data
	 */
	static Affiliation decodeAffiliation(String affiliation) {
		Affiliation result;
		switch (affiliation) {
			case "o":
				result = Affiliation.owner;
				break;
			case "p":
				result = Affiliation.publisher;
				break;
			case "u":
				result = Affiliation.publish_only;
				break;
			case "m":
				result = Affiliation.member;
				break;
			case "c":
				result = Affiliation.outcast;
				break;
			default:
				result = Affiliation.none;
		}

		return result;
	}

	/**
	 * Specification from ejabberd sources
	 *
	 * -spec decode_subscription(Arg :: binary()) -> atom().
	 * decode_subscription(<<"s">>) -> subscribed;
	 * decode_subscription(<<"p">>) -> pending;
	 * decode_subscription(<<"u">>) -> unconfigured;
	 * decode_subscription(_) -> none.
	 *
	 * Provided example data contained also: s:6088692ACAC13
	 *
	 * @param subString string data to be processed
	 *
	 * @return {@link Subscription} representation of read string data
	 */
	static Subscription decodeSubscription(String subString) {
		final String[] split = subString.split(":");

		String subValue = split.length > 0 ? split[0] : "";

		Subscription result;
		switch (subValue) {
			case "s":
				result = Subscription.subscribed;
				break;
			case "p":
				result = Subscription.pending;
				break;
			case "u":
				result = Subscription.unconfigured;
				break;
			default:
				result = Subscription.none;
				break;
		}
		return result;
	}

	static String getParent(String node) {
		if (node == null) {
			return null;
		}
		String parent = null;
		final int i = node.lastIndexOf('/');
		if (i > 0) {
			try {
				parent = node.substring(0, i);
			} catch (IndexOutOfBoundsException e) {
				//ignore
			}
		}
		return parent;
	}

	static String[] parseArrayValue(String value) {
		if (value != null) {
			final String trimmed = value.trim();
			if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
				final String content = trimmed.substring(1, value.trim().length() - 1);
				final String[] split = content.split("\\s*,\\s*");
				for (int i = 0; i < split.length; i++) {
					split[i] = split[i].trim();
				}
				return split;
			}
		}
		return null;
	}

	public PubSubNodeConverter() {
	}

	@Override
	public Optional<String> getMainQuery() {
		return queries.getQuery(PubSubQueries.NODES);
	}

	@Override
	public void initialise(Converter.ConverterProperties converterProperties) {
		this.properties = converterProperties;
		queries = new PubSubQueries(properties.getServerType(), properties.getDatabaseType());
	}

	@Override
	@SuppressWarnings("unchecked")
	public Optional<PubSubNodeEntity> processResultSet(ResultSet rs) throws Exception {

		// SELECT host, node, parent, plugin, nodeid FROM pubsub_node ORDER BY host, node DESC
		String node = null;
		String host = null;
		long node_id = 0;
		int counter = 0;
		String parent_node = null;

		switch (properties.getServerType()) {
			case ejabberd:
				node = rs.getString("node");
				host = rs.getString("host");
				node_id = rs.getLong("nodeid");
				counter = rs.getInt("counter");
				parent_node = rs.getString("parent");
				break;
		}
		if (node != null && host != null && node_id > 0) {
			final BareJID service = BareJID.bareJIDInstance(host);
			final String parent = parent_node != null && !parent_node.trim().isEmpty() ? parent_node : getParent(node);

			NodeType nodeType = counter > 0 ? NodeType.collection : NodeType.leaf;
			final PubSubNodeEntity pubSubNodeEntity = new PubSubNodeEntity(service, node, parent, nodeType, node_id);

			final List<BareJID> owners = (List<BareJID>) queryExecutor.executeQuery(PubSubQueries.NODE_OWNER,
																					getOwner(node_id));
			pubSubNodeEntity.addOwners(owners);

			final Map<BareJID, UserAssociation> associations = (Map<BareJID, UserAssociation>) queryExecutor.executeQuery(
					PubSubQueries.SUBSCRIPTIONS, getAssociation(node_id));

			associations.entrySet()
					.stream()
					.filter(entry -> Affiliation.owner.equals(entry.getValue().affiliation))
					.map(Map.Entry::getKey)
					.forEach(pubSubNodeEntity::addOwner);

			for (Map.Entry<BareJID, UserAssociation> association : associations.entrySet()) {
				pubSubNodeEntity.addSubscription(association.getKey(), association.getValue().subscription);
				pubSubNodeEntity.addAffiliation(association.getKey(), association.getValue().affiliation);
			}

			final Map<String, String> configuration = (Map<String, String>) queryExecutor.executeQuery(
					PubSubQueries.NODE_OPTIONS, getConfiguration(node_id));

			for (Map.Entry<String, String> entry : configuration.entrySet()) {
				final String key = entry.getKey();
				final String value = entry.getValue();
				setNodeConfiguration(pubSubNodeEntity, key, value);
			}

			return Optional.of(pubSubNodeEntity);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public boolean storeEntity(PubSubNodeEntity entity) throws Exception {

		final BareJID owner;
		if (!entity.getOwner().isEmpty()) {
			owner = entity.getOwner().iterator().next();
		} else {
			log.log(Level.FINE, "No owner defined for entity: " + entity);
			throw new IllegalStateException("No owner defined for: " + entity.getNode());
		}

		final INodeMeta nodeMeta = repository.getNodeMeta(entity.getService(), entity.getParent());
		// if the detected parent doesn't exist don't configure node to use it

		final String collection = nodeMeta != null ? entity.getParent() : null;
		entity.getNodeConfig().setCollection(collection);

		repository.createNode(entity.getService(), entity.getNode(), owner, entity.getNodeConfig(),
							  entity.getNodeType(), collection);

		final IAffiliations nodeAffiliations = repository.getNodeAffiliations(entity.getService(), entity.getNode());
		for (UsersAffiliation na : entity.getNodeAffiliations()) {
			nodeAffiliations.addAffiliation(na.getJid(), na.getAffiliation());
		}
		repository.update(entity.getService(), entity.getNode(), nodeAffiliations);
		for (BareJID ownerJid : entity.getOwner()) {
			nodeAffiliations.addAffiliation(ownerJid, Affiliation.owner);
		}

		final ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(entity.getService(), entity.getNode());
		for (UsersSubscription ns : entity.getNodeSubscriptions()) {
			nodeSubscriptions.addSubscriberJid(ns.getJid(), ns.getSubscription());
		}
		repository.update(entity.getService(), entity.getNode(), nodeSubscriptions);

		return true;
	}

	@Override
	public Map<String, String> getAdditionalQueriesToInitialise() {
		return queries.getAllQueriesForServerAndDatabase().orElse(Collections.emptyMap());
	}

	@Override
	public Optional<Class> getParentBean() {
		return Optional.of(PubSubComponent.class);
	}

	private void setNodeConfiguration(PubSubNodeEntity pubSubNodeEntity, String key, String value) {
		value = !"<<>>".equals(value) ? value : "";
		final String[] split = parseArrayValue(value);
		if (split != null && split.length > 0) {
			pubSubNodeEntity.setConfigValues(key, split);
		} else {
			pubSubNodeEntity.setConfigValue(key, value);
		}
	}

	private QueryExecutor.QueryFunction<PreparedStatement, Map<BareJID, UserAssociation>> getAssociation(Long node_id) {

		return preparedStatement -> {
			Map<BareJID, UserAssociation> associations = new ConcurrentHashMap<>();

			ResultSet resultSet = null;

			// "SELECT jid, affiliation, subscriptions, stateid FROM pubsub_state WHERE nodeid = ?";
			preparedStatement.setString(1, String.valueOf(node_id));
			resultSet = preparedStatement.executeQuery();
			while (resultSet.next()) {
				final String jidString = resultSet.getString("jid");
				final BareJID jid = BareJID.bareJIDInstance(jidString);

				final String affiliationString = resultSet.getString("affiliation");
				final Affiliation affiliation = decodeAffiliation(affiliationString);

				final String subString = resultSet.getString("subscriptions");
				final Subscription subscription = decodeSubscription(subString);

				associations.put(jid, new UserAssociation(subscription, affiliation));
			}
			resultSet.close();
			return associations;
		};
	}

	private QueryExecutor.QueryFunction<PreparedStatement, Map<String, String>> getConfiguration(Long node_id) {

		return preparedStatement -> {
			Map<String, String> options = new ConcurrentHashMap<>();

			ResultSet resultSet = null;

			// "SELECT name, val FROM pubsub_node_option WHERE nodeid = ?";
			preparedStatement.setString(1, String.valueOf(node_id));
			resultSet = preparedStatement.executeQuery();
			while (resultSet.next()) {
				final String name = resultSet.getString("name");
				final String value = resultSet.getString("val");
				options.put(name, value);
			}
			resultSet.close();
			return options;
		};
	}

	private QueryExecutor.QueryFunction<PreparedStatement, List<BareJID>> getOwner(Long node_id) {
		return preparedStatement -> {
			List<BareJID> items = new CopyOnWriteArrayList<>();

			ResultSet resultSet = null;

			// "SELECT owner FROM pubsub_node_owner where nodeid = ?";
			preparedStatement.setString(1, String.valueOf(node_id));
			resultSet = preparedStatement.executeQuery();
			while (resultSet.next()) {
				final String ownerString = resultSet.getString("owner");
				final BareJID owner = BareJID.bareJIDInstance(ownerString);
				items.add(owner);
			}
			resultSet.close();
			return items;
		};
	}

	private class UserAssociation {

		Affiliation affiliation;
		Subscription subscription;

		UserAssociation(Subscription subscription, Affiliation affiliation) {
			this.subscription = subscription;
			this.affiliation = affiliation;
		}
	}
}
