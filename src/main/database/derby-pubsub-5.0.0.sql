--
-- Tigase PubSub - Publish Subscribe component for Tigase
-- Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, version 3 of the License.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program. Look for COPYING file in the top folder.
-- If not, see http://www.gnu.org/licenses/.
--

--
-- QUERY START:
create table tig_pubsub_mam (
	node_id bigint not null references tig_pubsub_nodes ( node_id ) on delete cascade,
	uuid varchar(36) not null,

	item_id varchar(1024),
	ts timestamp not null,
	data varchar(32672),

	primary key ( node_id, uuid )
);
-- QUERY END:

-- QUERY START:
create index tig_pubsub_mam_node_id_item_id on tig_pubsub_mam ( node_id, item_id );
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamAddItem(node_id bigint, uuid varchar(36),
	ts timestamp, item_data varchar(32672), item_id varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubMamAddItem';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubQueryItems(nodes_ids varchar(32672), since timestamp, "to" timestamp, "order" int, "limit" int, "offset" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubQueryItems';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubQueryItemPosition(nodes_ids varchar(32672), since timestamp, "to" timestamp, "order" int, "node_id" bigint, "item_id" varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubQueryItemPosition';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubQueryItemsCount(nodes_ids varchar(32672), since timestamp, "to" timestamp, "order" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubQueryItemsCount';
-- QUERY END:
