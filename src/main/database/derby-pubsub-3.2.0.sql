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
create procedure TigPubSubGetNodeMeta(service_jid varchar(2049), node_name varchar(1024))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.pubsub.repository.derby.StoredProcedures.tigPubSubGetNodeMeta';
-- QUERY END:

-- QUERY START:
call TigSetComponentVersion('pubsub', '3.2.0');
-- QUERY END:
