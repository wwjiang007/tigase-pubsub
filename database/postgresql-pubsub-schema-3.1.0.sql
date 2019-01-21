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

\i database/postgresql-pubsub-schema-3.0.0.sql

-- LOAD FILE: database/postgresql-pubsub-schema-3.0.0.sql

-- QUERY START:
create or replace function TigPubSubRemoveService(varchar(2049)) returns void as $$
	delete from tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_nodes where node_id in (
		select n.node_id from tig_pubsub_nodes n 
			inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
			where sj.service_jid = $1);
	delete from tig_pubsub_service_jids where service_jid = $1;
	delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid = $1);
	delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid = $1);
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubWriteItem(bigint,varchar(1024),varchar(2049),text) returns void as $$
declare
	_node_id alias for $1;
	_item_id alias for $2;
	_publisher alias for $3;
	_item_data alias for $4;
	_publisher_id bigint;
begin
	if exists (select 1 from tig_pubsub_items where node_id = _node_id and id = _item_id) then
		update tig_pubsub_items set update_date = (now() at time zone 'utc'), data = _item_data 
			where node_id = _node_id and id = _item_id;
	else
		select TigPubSubEnsureJid(_publisher) into _publisher_id;
		insert into tig_pubsub_items (node_id, id, creation_date, update_date, publisher_id, data)
			values (_node_id, _item_id, (now() at time zone 'utc'), (now() at time zone 'utc'), _publisher_id, _item_data);
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END: