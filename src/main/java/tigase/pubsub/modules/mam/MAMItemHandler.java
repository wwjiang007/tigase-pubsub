/*
 * MAMItemHandler.java
 *
 * Tigase PubSub Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package tigase.pubsub.modules.mam;

import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.utils.Logic;
import tigase.xml.Element;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by andrzej on 25.12.2016.
 */
@Bean(name = "mamItemHandler", parent = MAMQueryModule.class)
public class MAMItemHandler extends tigase.xmpp.mam.MAMItemHandler {

	private long idCounter = 0;

	@Inject
	private Logic logic;

	@Override
	public void itemFound(Query query, MAMRepository.Item item) {
		if (item instanceof IPubSubRepository.Item) {
			Map<String,String> headers = new HashMap<>();
			headers.put("Collection", ((IPubSubRepository.Item) item).getNode());
			Element itemEl = item.getMessage();
			Element itemsEl = new Element("items", new String[] { "node" }, new String[] { ((IPubSubRepository.Item) item).getNode() });
			itemsEl.addChild(itemEl);
			Element message = logic.prepareNotificationMessage(query.getComponentJID(), query.getQuestionerJID(), String.valueOf(++this.idCounter), itemsEl, headers);
			((PubSubDAO.Item) item).setMessage(message);
		}
		super.itemFound(query, item);
	}
}
