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
drop procedure if exists TigPubSubGetNodeItemsIds;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeItemsIdsSince;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigPubSubGetNodeItemsIds(_node_id bigint, _order int)
begin
    if _order = 1 then
        select id from tig_pubsub_items where node_id = _node_id order by creation_date;
    else
        select id from tig_pubsub_items where node_id = _node_id order by update_date;
    end if;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsIdsSince(_node_id bigint, _order int, _since datetime)
begin
    if _order = 1 then
        select id from tig_pubsub_items where node_id = _node_id
                                          and creation_date >= _since order by creation_date;
    else
        select id from tig_pubsub_items where node_id = _node_id
                                          and update_date >= _since order by update_date;
    end if;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
call TigSetComponentVersion('pubsub', '4.1.0');
-- QUERY END:
