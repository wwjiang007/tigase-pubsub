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
do $$
begin
if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_pubsub_service_jids' and column_name = 'component_name') then
    alter table tig_pubsub_service_jids add column component_name varchar(190);

    update tig_pubsub_service_jids set component_name = (select name from (
        select name
        from (
            select distinct LEFT(service_jid, POSITION('.' in service_jid)) as name
            from tig_pubsub_service_jids where service_jid not like '%@%' and POSITION('.' in service_jid) > 0
        ) x
        union
        select 'pubsub' as name
    ) y limit 1) where component_name is null;

    alter table tig_pubsub_service_jids alter column component_name set not null;
    create index tig_pubsub_service_jids_component_name on tig_pubsub_service_jids ( component_name );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_nodes_collection_id_service_id')) is null) then
    create index tig_pubsub_nodes_collection_id_service_id on tig_pubsub_nodes (collection_id, service_id);

    drop index tig_pubsub_nodes_collection_id;
    drop index tig_pubsub_nodes_name;
    drop index tig_pubsub_nodes_service_id;
end if;
end$$;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubEnsureServiceJid(varchar(2049));
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubEnsureServiceJid(varchar(2049),varchar(190)) returns bigint as '
declare
	_service_jid alias for $1;
	_component_name alias for $2;
	_service_id bigint;
begin
	select service_id into _service_id from tig_pubsub_service_jids where lower(service_jid) = lower(_service_jid);
	if (_service_id is null) then
		insert into tig_pubsub_service_jids (service_jid, component_name) values (_service_jid,_component_name);
		select currval(''tig_pubsub_service_jids_service_id_seq'') into _service_id;
	end if;
	return _service_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubCreateNode(varchar,varchar,int,varchar,text,bigint);
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024), _node_type int, _node_creator varchar(2049), _node_conf text, _collection_id bigint, _ts timestamp with time zone, _component_name varchar(190)) returns bigint as $$
declare
    _service_id bigint;
    _node_creator_id bigint;
    _node_id bigint;
begin
	select TigPubSubEnsureServiceJid(_service_jid, _component_name) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;
	insert into tig_pubsub_nodes (service_id, name, "type", creator_id, creation_date, configuration, collection_id)
		values (_service_id, _node_name, _node_type, _node_creator_id, _ts, _node_conf, _collection_id);
	select currval('tig_pubsub_nodes_node_id_seq') into _node_id;
	return _node_id;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubRemoveService(varchar(2049));
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubRemoveService(varchar(2049),varchar(190)) returns void as $$
delete from tig_pubsub_items where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1) and sj.component_name = $2);
delete from tig_pubsub_affiliations where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1) and sj.component_name = $2);
delete from tig_pubsub_subscriptions where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1) and sj.component_name = $2);
delete from tig_pubsub_nodes where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1) and sj.component_name = $2);
delete from tig_pubsub_service_jids where lower(service_jid) = lower($1) and component_name = $2;
delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where lower(j.jid) = lower($1));
delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where lower(j.jid) = lower($1));
$$ LANGUAGE SQL;
-- QUERY END: