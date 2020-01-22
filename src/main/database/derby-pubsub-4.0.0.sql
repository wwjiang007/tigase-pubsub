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

-- QUERY START:
alter table tig_pubsub_service_jids add column service_jid_sha1 varchar(50);
-- QUERY END:

-- QUERY START:
alter table tig_pubsub_jids add column jid_sha1 varchar(50);
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItems(node_id bigint, since timestamp, "to" timestamp, "limit" int, "offset" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubMamQueryItems';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemPosition(node_id bigint, since timestamp, "to" timestamp, "uuid" varchar(36))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubMamQueryItemPosition';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemsCount(node_id bigint, since timestamp, "to" timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubMamQueryItemsCount';
-- QUERY END:

-- QUERY START:
create procedure TigPubSubCountNodes(serviceJid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubCountNodes';
-- QUERY END:

-- QUERY START:
call TigSetComponentVersion('pubsub', '4.0.0');
-- QUERY END:
