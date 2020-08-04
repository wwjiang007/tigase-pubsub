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
if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_pubsub_service_jids' and column_name = 'domain') then
    alter table tig_pubsub_service_jids add column domain varchar(1024);
    alter table tig_pubsub_service_jids add column is_public int default 0;

    update tig_pubsub_service_jids set domain = CASE POSITION('@' in service_jid)
        WHEN 0 THEN service_jid
        ELSE SUBSTRING(service_jid, POSITION('@' in service_jid) + 1)
        end;

    alter table tig_pubsub_service_jids alter column domain set not null;
    create index tig_pubsub_service_jids_domain_is_public on tig_pubsub_service_jids ( domain, is_public );
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
do $$
begin
if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_pubsub_items' and column_name = 'uuid') then
    alter table tig_pubsub_items add column uuid uuid;
end if;
end$$;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubEnsureServiceJid(varchar(2049));
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubEnsureServiceJid(varchar(2049),varchar(1024),int) returns bigint as '
declare
	_service_jid alias for $1;
	_domain alias for $2;
	_createService alias for $3;
	_service_id bigint;
begin
	select service_id into _service_id from tig_pubsub_service_jids where lower(service_jid) = lower(_service_jid);
	if (_service_id is null) and _createService > 0 then
		insert into tig_pubsub_service_jids (service_jid, domain) values (_service_jid,_domain);
		select currval(''tig_pubsub_service_jids_service_id_seq'') into _service_id;
	end if;
	return _service_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubEnsureJid(varchar(2049)) returns bigint as '
declare
	_jid alias for $1;
	_jid_id bigint;
begin
	select jid_id into _jid_id from tig_pubsub_jids where lower(jid) = lower(_jid);
	if _jid_id is null then
		insert into tig_pubsub_jids (jid) values (_jid) on conflict do nothing;
		select jid_id into _jid_id from tig_pubsub_jids where lower(jid) = lower(_jid);
	end if;
	return _jid_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubCreateNode(varchar,varchar,int,varchar,text,bigint);
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024), _node_type int, _node_creator varchar(2049), _node_conf text, _collection_id bigint, _ts timestamp with time zone, _domain varchar(1024), _createService int) returns bigint as $$
declare
    _service_id bigint;
    _node_creator_id bigint;
    _node_id bigint;
begin
	select TigPubSubEnsureServiceJid(_service_jid, _domain, _createService) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;
	insert into tig_pubsub_nodes (service_id, name, "type", creator_id, creation_date, configuration, collection_id)
		values (_service_id, _node_name, _node_type, _node_creator_id, _ts, _node_conf, _collection_id);
	select currval('tig_pubsub_nodes_node_id_seq') into _node_id;
	return _node_id;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubRemoveService(varchar(2049)) returns void as $$
delete from tig_pubsub_items where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1));
delete from tig_pubsub_affiliations where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1));
delete from tig_pubsub_subscriptions where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1));
delete from tig_pubsub_nodes where node_id in (
    select n.node_id from tig_pubsub_nodes n
                              inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
    where lower(sj.service_jid) = lower($1));
delete from tig_pubsub_service_jids where lower(service_jid) = lower($1);
delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where lower(j.jid) = lower($1));
delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where lower(j.jid) = lower($1));
$$ LANGUAGE SQL;
-- QUERY END:


-- MAM TABLE

-- QUERY START:
create table if not exists tig_pubsub_mam (
    node_id bigint not null references tig_pubsub_nodes ( node_id ) on delete cascade,
    uuid uuid not null,
    item_id varchar(1024),
    ts timestamp with time zone not null,
    data text
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('tig_pubsub_mam_node_id_item_id')) is null) then
    create index tig_pubsub_mam_node_id_item_id on tig_pubsub_mam ( node_id, item_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubDeleteItem(bigint,varchar(1024)) returns void as $$
	delete from tig_pubsub_items where node_id = $1 and id = $2;
	update tig_pubsub_mam set data = null where node_id = $1 and item_id = $2;
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubWriteItem(bigint,varchar(1024),varchar(2049),text, timestamp with time zone);
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubWriteItem(bigint,varchar(1024),varchar(2049),text, timestamp with time zone, varchar(36)) returns void as $$
declare
	_node_id alias for $1;
	_item_id alias for $2;
	_publisher alias for $3;
	_item_data alias for $4;
	_ts alias for $5;
	_uuid alias for $6;
	_publisher_id bigint;
begin
	if exists (select 1 from tig_pubsub_items where node_id = _node_id and id = _item_id) then
		update tig_pubsub_items set update_date = _ts, data = _item_data, uuid = uuid(_uuid)
			where node_id = _node_id and id = _item_id;
	else
		select TigPubSubEnsureJid(_publisher) into _publisher_id;
		insert into tig_pubsub_items (node_id, id, creation_date, update_date, publisher_id, data, uuid)
			values (_node_id, _item_id, _ts, _ts, _publisher_id, _item_data, uuid(_uuid));
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubMamAddItem(bigint, varchar(36), timestamp with time zone, text, varchar(1024)) returns void as $$
declare
	_node_id alias for $1;
	_uuid alias for $2;
	_ts alias for $3;
	_item_data alias for $4;
	_item_id alias for $5;
begin
    insert into tig_pubsub_mam (node_id, uuid, ts, data, item_id)
	    values (_node_id, uuid(_uuid), _ts, _item_data, _item_id);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('TigPubSubMamQueryItems') and pg_get_function_arguments(oid) = '_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _publisher character varying, _order integer, _limit integer, _offset integer') then
    drop function TigPubSubMamQueryItems(_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _publisher character varying, _order integer, _limit integer, _offset integer);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubMamQueryItems(_node_id bigint, _since timestamp with time zone , _to timestamp with time zone, _limit int, _offset int) returns table (
    uuid varchar(36),
    ts timestamp with time zone,
    payload text
) as $$
    select pm.uuid::text, pm.ts, pm.data
        from tig_pubsub_mam pm
        where
            pm.node_id = _node_id
            and (_since is null or pm.ts >= _since)
            and (_to is null or pm.ts <= _to)
        order by pm.ts
        limit _limit offset _offset
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('TigPubSubMamQueryItemPosition') and pg_get_function_arguments(oid) = '_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _publisher character varying, _order integer, _node_id bigint, _item_id character varying') then
    drop function TigPubSubMamQueryItemPosition(_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _publisher character varying, _order integer, _node_id bigint, _item_id character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubMamQueryItemPosition(_node_id bigint, _since timestamp with time zone, _to timestamp with time zone, _uuid varchar(36)) returns table (
    "position" bigint
) as $$
    select x.position from (
	    select row_number() over (w) as position, pm.uuid
        from tig_pubsub_mam pm
        where
            pm.node_id = _node_id
            and (_since is null or pm.ts >= _since)
            and (_to is null or pm.ts <= _to)
            window w as (order by pm.ts)
        ) x where x.uuid = uuid(_uuid)
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('TigPubSubMamQueryItemsCount') and pg_get_function_arguments(oid) = '_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _publisher character varying, _order integer') then
    drop function TigPubSubMamQueryItemsCount(_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _publisher character varying, _order integer);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubMamQueryItemsCount(_node_id bigint, _since timestamp with time zone, _to timestamp with time zone) returns table (
    "count" bigint
) as $$
    select count(1)
        from tig_pubsub_mam pm
        where
            pm.node_id = _node_id
            and (_since is null or pm.ts >= _since)
            and (_to is null or pm.ts <= _to)
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('TigPubSubGetItem') and pg_get_function_result(oid) = 'TABLE(data text, jid character varying, creation_date timestamp with time zone, update_date timestamp with time zone)') then
    drop function TigPubSubGetItem(bigint, character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetItem(bigint,varchar(1024)) returns table (
	"data" text, jid varchar(2049), uuid varchar(36)
) as $$
	select "data", pn.name, pi.uuid::text
		from tig_pubsub_items pi
		inner join tig_pubsub_nodes pn on pn.node_id = pi.node_id
		where pi.node_id = $1 and pi.id = $2
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('TigPubSubQueryItems') and pg_get_function_result(oid) = 'TABLE(node_name character varying, node_id bigint, item_id character varying, creation_date timestamp with time zone, payload text)') then
    drop function TigPubSubQueryItems(text, timestamp with time zone, timestamp with time zone, int, int, int);
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
    if exists( select 1 from pg_proc where proname = lower('TigPubSubQueryItems') and pg_get_function_result(oid) = 'TABLE(node_name character varying, item_id character varying, uuid character varying, payload text)') then
    drop function TigPubSubQueryItems(text, timestamp with time zone, timestamp with time zone, int, int, int);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubQueryItems(_nodes_ids text, _since timestamp with time zone , _to timestamp with time zone, _order int, _limit int, _offset int) returns table (
    node_name varchar(1024),
    node_id bigint,
    item_id varchar(1024),
    uuid varchar(36),
    payload text
) as $$
declare
	nodesIds text;
begin
    nodesIds := '{' || _nodes_ids || '}';

    if _order = 0 then
        return query select pn.name, pi.node_id, pi.id, cast(pi.uuid::text as varchar(36)) as uuid, pi.data
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (select unnest(nodesIds::bigint[]))
            and (_since is null or pi.creation_date >= _since)
            and (_to is null or pi.creation_date <= _to)
        order by pi.creation_date
        limit _limit offset _offset;
    else
        return query select pn.name, pi.node_id, pi.id, cast(pi.uuid::text as varchar(36)) as uuid, pi.data
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (select unnest(nodesIds::bigint[]))
            and (_since is null or pi.update_date >= _since)
            and (_to is null or pi.update_date <= _to)
        order by pi.update_date
        limit _limit offset _offset;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubQueryItemPosition(_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _order int, _node_id bigint, _item_id varchar(1024)) returns table (
    "position" bigint
) as $$
declare
	nodesIds text;
begin
    nodesIds := '{' || _nodes_ids || '}';

    if _order = 0 then
        return query select x.position from (
		    select row_number() over (w) as position, pi.node_id, id
            from tig_pubsub_items pi
            where
                pi.node_id in (select unnest(nodesIds::bigint[]))
                and (_since is null or pi.creation_date >= _since)
                and (_to is null or pi.creation_date <= _to)
            window w as (order by pi.creation_date)
            ) x where x.node_id = _node_id and x.id = _item_id;
    else
        return query select x.position from (
		    select row_number() over (w) as position, pi.node_id, id
            from tig_pubsub_items pi
            where
                pi.node_id in (select unnest(nodesIds::bigint[]))
                and (_since is null or pi.update_date >= _since)
                and (_to is null or pi.update_date <= _to)
            window w as (order by pi.update_date)
            ) x where x.node_id = _node_id and x.id = _item_id;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubQueryItemsCount(_nodes_ids text, _since timestamp with time zone, _to timestamp with time zone, _order int) returns table (
    "count" bigint
) as $$
declare
	nodesIds text;
begin
    nodesIds := '{' || _nodes_ids || '}';

    if _order = 0 then
        return query select count(1)
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (select unnest(nodesIds::bigint[]))
            and (_since is null or pi.creation_date >= _since)
            and (_to is null or pi.creation_date <= _to);
    else
        return query select count(1)
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (select unnest(nodesIds::bigint[]))
            and (_since is null or pi.update_date >= _since)
            and (_to is null or pi.update_date <= _to);
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('TigPubSubGetNodeItemsMeta') and pg_get_function_result(oid) = 'TABLE(id character varying, creation_date timestamp with time zone, update_date timestamp with time zone)') then
    drop function TigPubSubGetNodeItemsMeta(bigint);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetNodeItemsMeta(bigint)
		returns table (id varchar(1024), creation_date timestamp with time zone, update_date timestamp with time zone, uuid varchar(36)) as $$
	select id, creation_date, update_date, uuid::text from tig_pubsub_items where node_id = $1 order by creation_date
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetServices(varchar(1024),int)
		returns table (service_jid varchar(2049), is_public int) as $$
	select service_jid, is_public from tig_pubsub_service_jids where domain = $1 and ($2 is null or is_public = $2) order by service_jid;
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubCreateService(varchar(2049),varchar(1024),int)
		returns void as $$
	insert into tig_pubsub_service_jids (service_jid, domain, is_public) values ($1, $2, $3);
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubRemoveNode(bigint) returns void as '
declare
	_node_id alias for $1;
begin
    delete from tig_pubsub_mam where node_id = _node_id;
	delete from tig_pubsub_items where node_id = _node_id;
	delete from tig_pubsub_subscriptions where node_id = _node_id;
	delete from tig_pubsub_affiliations where node_id = _node_id;
	delete from tig_pubsub_nodes where node_id = _node_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

