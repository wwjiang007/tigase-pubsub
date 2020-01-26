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
package tigase.pubsub.repository.converter;

import tigase.db.converter.Converter;
import tigase.db.converter.Convertible;
import tigase.db.converter.QueryExecutor;
import tigase.kernel.beans.Inject;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.jid.BareJID;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.logging.Logger;

public class PubSubItemsConverter
		implements Convertible<PubSubItemEntity> {

	private final static Logger log = Logger.getLogger(PubSubItemsConverter.class.getName());
	@Inject
	QueryExecutor queryExecutor;
	private SimpleParser parser = SingletonFactory.getParserInstance();
	private Converter.ConverterProperties properties;
	private PubSubQueries queries;
	@Inject(nullAllowed = false)
	private IPubSubRepository repository;

	@Override
	public Optional<Class> getParentBean() {
		return Optional.of(PubSubComponent.class);
	}

	@Override
	public Optional<Class<? extends Convertible>> dependsOn() {
		return Optional.of(PubSubNodeConverter.class);
	}

	@Override
	public Optional<String> getMainQuery() {
		return queries.getQuery(PubSubQueries.ITEMS);
	}

	@Override
	public void initialise(Converter.ConverterProperties converterProperties) {
		this.properties = converterProperties;
		queries = new PubSubQueries(properties.getServerType(), properties.getDatabaseType());
	}

	@Override
	@SuppressWarnings("unchecked")
	public Optional<PubSubItemEntity> processResultSet(ResultSet rs) throws Exception {

		// SELECT pubsub_item.nodeid, host, node, itemid, publisher, creation, modification, payload FROM pubsub_item LEFT JOIN pubsub_node on pubsub_item.nodeid = pubsub_node.nodeid
		String node = null;
		String host = null;
		String itemid = null;
		String publisher = null;
//		String creation = null;
		String payloadStr = null;

		switch (properties.getServerType()) {
			case ejabberd:
				node = rs.getString("node");
				host = rs.getString("host");
				itemid = rs.getString("itemid");
				publisher = rs.getString("publisher");
//				creation = rs.getString("creation");
				payloadStr = rs.getString("payload");
				break;
		}
		final Optional<Element> payload = parsePayload(payloadStr);
		if (node != null && host != null && publisher != null && payload.isPresent()) {
			final BareJID service = BareJID.bareJIDInstance(host);

			final PubSubItemEntity pubSubNodeEntity = new PubSubItemEntity(service, node, itemid, publisher,
																		   payload.get());
			return Optional.of(pubSubNodeEntity);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public boolean storeEntity(PubSubItemEntity entity) throws Exception {
		IItems nodeItems = repository.getNodeItems(entity.getService(), entity.getNode());
		nodeItems.writeItem(entity.getItemid(), entity.getPublisher(), entity.getPayload(), null);
		return true;
	}

	@Override
	public Map<String, String> getAdditionalQueriesToInitialise() {
		return queries.getAllQueriesForServerAndDatabase().orElse(Collections.emptyMap());
	}

	private Optional<Element> parsePayload(String payloadStr) {
		if (payloadStr != null) {
			DomBuilderHandler domHandler = new DomBuilderHandler();
			char[] data = payloadStr.toCharArray();
			parser.parse(domHandler, data, 0, data.length);

			final Queue<Element> parsedElements = domHandler.getParsedElements();
			return parsedElements.peek() != null ? Optional.of(parsedElements.poll()) : Optional.empty();
		} else {
			return Optional.empty();
		}
	}
}
