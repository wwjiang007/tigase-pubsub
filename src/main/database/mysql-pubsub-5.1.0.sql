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

delimiter ;

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItem;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigPubSubMamQueryItem(_node_id bigint, _uuid varchar(36), _offset int)
begin
select TigPubSubOrderedToUuid(pm.uuid), pm.ts, pm.data
from tig_pubsub_mam pm
where
        pm.node_id = _node_id and pm.uuid = TigPubSubUuidToOrdered(_uuid);
end //
-- QUERY END:

delimiter ;