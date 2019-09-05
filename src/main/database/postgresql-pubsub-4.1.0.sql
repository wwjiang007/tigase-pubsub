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
create or replace function TigPubSubGetNodeItemsIds(bigint, int) returns table (id varchar(1024)) as $$
declare
    _node_id alias for $1;
    _order alias for $2;
begin
    if _order = 1 then
        return query select i.id from tig_pubsub_items i where i.node_id = _node_id order by i.creation_date;
    else
        return query select i.id from tig_pubsub_items i where i.node_id = _node_id order by i.update_date;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetNodeItemsIdsSince(bigint, int, timestamp with time zone) returns table (id varchar(1024)) as $$
declare
    _node_id alias for $1;
    _order alias for $2;
    _since alias for $3;
begin
    if _order = 1 then
        return query select i.id from tig_pubsub_items where i.node_id = _node_id and i.creation_date >= _since order by i.creation_date;
    else
        return query select i.id from tig_pubsub_items where i.node_id = _node_id and i.update_date >= _since order by i.update_date;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:
