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

import tigase.db.converter.RowEntity;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;

/**
 * CREATE TABLE [dbo].[pubsub_item] (
 * [nodeid] [bigint] NULL,
 * [itemid] [varchar] (255) NOT NULL,
 * [publisher] [text] NOT NULL,
 * [creation] [varchar] (32) NOT NULL,
 * [modification] [varchar] (32) NOT NULL,
 * [payload] [text] NOT NULL DEFAULT ''
 * ) TEXTIMAGE_ON [PRIMARY];
 */
class PubSubItemEntity
		implements RowEntity {

	private final String itemid;
	private final String node;
	private final Element payload;
	private final String publisher;
//	private final Long creation;
	private final BareJID service;

	public PubSubItemEntity(BareJID service, String node, String itemid, String publisher, Element payload) {
		this.node = node;
		this.service = service;
		this.itemid = itemid;
		this.publisher = publisher;
		this.payload = payload;
//		this.creation = creation;
	}

	public String getItemid() {
		return itemid;
	}

	public String getNode() {
		return node;
	}

	public Element getPayload() {
		return payload;
	}

	public String getPublisher() {
		return publisher;
	}

	public BareJID getService() {
		return service;
	}

	@Override
	public String getID() {
		return String.format("%1$s / %2$s / %3$s", service, node, itemid);
	}
}
