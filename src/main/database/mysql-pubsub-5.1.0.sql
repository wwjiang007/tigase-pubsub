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

-- QUERY START:
drop procedure if exists TigPubSubQueryItemPosition;
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

-- QUERY START:
create procedure TigPubSubMamQueryItemPosition(_node_id bigint, _since timestamp(6), _to timestamp(6), _uuid varchar(36))
begin
select count(1) + 1 as position
from tig_pubsub_mam pm1 inner join
    (select ts, item_id from tig_pubsub_mam pm2 where pm2.node_id = _node_id and pm2.uuid = TigPubSubUuidToOrdered(_uuid)) x
where
    pm1.node_id = _node_id
  and pm1.ts < x.ts
  and (_since is null or pm1.ts >= _since)
  and (_to is null or pm1.ts <= _to);
end //
-- QUERY END:
    
delimiter ;