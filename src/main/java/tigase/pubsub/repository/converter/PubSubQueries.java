/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2018 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.pubsub.repository.converter;

import tigase.db.DataRepository;
import tigase.db.converter.Converter;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class PubSubQueries {

	final static String NODES = "SELECT host, node, parent, nodeid, ( SELECT COUNT(*) FROM `pubsub_node` WHERE pubsub_node.host = outer_pubsub_node.host AND `node` like CONCAT(outer_pubsub_node.node, '/%')) AS counter FROM pubsub_node as outer_pubsub_node order by host, counter DESC, node";
	final static String NODE_OPTIONS = "SELECT name, val FROM pubsub_node_option WHERE nodeid = ?";
	final static String NODE_OWNER = "SELECT owner FROM pubsub_node_owner where nodeid = ?";
	final static String SUBSCRIPTIONS = "SELECT jid, affiliation, subscriptions, stateid FROM pubsub_state WHERE nodeid = ?";
	final static String SUBSCRIPTION_OPT = "SELECT subid, opt_name, opt_value FROM pubsub_subscription_opt";
	final static String ITEMS = "SELECT pubsub_item.nodeid, host, node, itemid, publisher, creation, modification, payload FROM pubsub_item LEFT JOIN pubsub_node on pubsub_item.nodeid = pubsub_node.nodeid order by host, node, creation";

	final Map<String, String> selectedQueries;
	DataRepository.dbTypes dbType;
	// Converter.SERVER [type] / DataRepository.dbTypes / query
	Map<String, Map<String, Map<String, String>>> queries = new ConcurrentHashMap<>();
	Converter.SERVER serverType;

	PubSubQueries(Converter.SERVER serverType, DataRepository.dbTypes dbType) {
		this.serverType = serverType;
		this.dbType = dbType;
		final Map<String, Map<String, String>> ejabberdQueries = queries.computeIfAbsent(
				Converter.SERVER.ejabberd.name(), k -> new ConcurrentHashMap<>());
		final Map<String, String> ejabberdSqlGeneric = new ConcurrentHashMap<>();

		ejabberdSqlGeneric.put(ITEMS, ITEMS);
		ejabberdSqlGeneric.put(NODES, NODES);
		ejabberdSqlGeneric.put(NODE_OPTIONS, NODE_OPTIONS);
		ejabberdSqlGeneric.put(NODE_OWNER, NODE_OWNER);
		ejabberdSqlGeneric.put(SUBSCRIPTIONS, SUBSCRIPTIONS);
		ejabberdSqlGeneric.put(SUBSCRIPTION_OPT, SUBSCRIPTION_OPT);

		ejabberdQueries.put(DataRepository.dbTypes.sqlserver.name(), ejabberdSqlGeneric);
		ejabberdQueries.put(DataRepository.dbTypes.jtds.name(), ejabberdSqlGeneric);
		ejabberdQueries.put(DataRepository.dbTypes.mysql.name(), ejabberdSqlGeneric);
		ejabberdQueries.put(DataRepository.dbTypes.postgresql.name(), ejabberdSqlGeneric);

		selectedQueries = getAllQueriesForServerAndDatabase().orElse(Collections.emptyMap());
	}

	Optional<String> getQuery(String query) {
		return Optional.ofNullable(selectedQueries.get(query));
	}

	Optional<Map<String, String>> getAllQueriesForServerAndDatabase() {
		final Map<String, Map<String, String>> orDefault = queries.getOrDefault(serverType.name(),
																				Collections.emptyMap());
		final Map<String, String> value = orDefault.get(dbType.name());
		return Optional.ofNullable(value);
	}

}
