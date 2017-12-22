--
--  Tigase PubSub Component
--  Copyright (C) 2016 "Tigase, Inc." <office@tigase.com>
--
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU Affero General Public License as published by
--  the Free Software Foundation, either version 3 of the License.
--
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU Affero General Public License for more details.
--
--  You should have received a copy of the GNU Affero General Public License
--  along with this program. Look for COPYING file in the top folder.
--  If not, see http://www.gnu.org/licenses/.

-- QUERY START:
alter table tig_pubsub_service_jids add column service_jid_sha1 varchar(50);
-- QUERY END:

-- QUERY START:
alter table tig_pubsub_jids add column jid_sha1 varchar(50);
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItems(nodes_ids varchar(32672), since timestamp, "to" timestamp, "publisher" varchar(1024), "order" int, "limit" int, "offset" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubMamQueryItems';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemPosition(nodes_ids varchar(32672), since timestamp, "to" timestamp, "publisher" varchar(1024), "order" int, "node_id" bigint, "item_id" varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubMamQueryItemPosition';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemsCount(nodes_ids varchar(32672), since timestamp, "to" timestamp, "publisher" varchar(1024), "order" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubMamQueryItemsCount';
-- QUERY END:

-- QUERY START:
call TigSetComponentVersion('pubsub', '4.0.0');
-- QUERY END:
