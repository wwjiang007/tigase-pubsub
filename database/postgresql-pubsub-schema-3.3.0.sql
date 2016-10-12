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

\i database/postgresql-pubsub-schema-3.2.0.sql

-- LOAD FILE: database/postgresql-pubsub-schema-3.2.0.sql

-- QUERY START:
do $$
begin
if exists (select 1 where (select pg_get_indexdef(oid) from pg_class i  where i.relname = 'tig_pubsub_service_jids_service_jid') not like '%lower(service_jid)%') then
    drop index tig_pubsub_service_jids_service_jid;
    create unique index tig_pubsub_service_jids_service_jid on tig_pubsub_service_jids ( lower(service_jid) );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select pg_get_indexdef(oid) from pg_class i  where i.relname = 'tig_pubsub_jids_jid') not like '%lower(jid)%') then
    drop index tig_pubsub_jids_jid;
    create unique index tig_pubsub_jids_jid on tig_pubsub_jids ( lower(jid) );
end if;
end$$;
-- QUERY END:

-- -----------------------------------------------------------------------------
-- Functions
-- -----------------------------------------------------------------------------
-- QUERY START:
create or replace function TigPubSubEnsureServiceJid(varchar(2049)) returns bigint as '
declare
	_service_jid alias for $1;
	_service_id bigint;
begin
	select service_id into _service_id from tig_pubsub_service_jids where lower(service_jid) = lower(_service_jid);
	if (_service_id is null) then
		insert into tig_pubsub_service_jids (service_jid) values (_service_jid);
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
		insert into tig_pubsub_jids (jid) values (_jid);
		select currval(''tig_pubsub_jids_jid_id_seq'') into _jid_id;
	end if;
	return _jid_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetNodeId(varchar(2049),varchar(1024)) returns table (node_id bigint) as $$
	select n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where lower(sj.service_jid) = lower($1) and n.name = $2
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetAllNodes(varchar(2049)) returns table (name varchar(1024), node_id bigint) as $$
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where lower(sj.service_jid) = lower($1)
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetRootNodes(varchar(2049)) returns table (name varchar(1024), node_id bigint) as $$
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where lower(sj.service_jid) = lower($1) and n.collection_id is null
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetChildNodes(varchar(2049),varchar(1024)) returns table (name varchar(1024), node_id bigint) as $$
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		inner join tig_pubsub_nodes p on p.node_id = n.collection_id and p.service_id = sj.service_id
		where lower(sj.service_jid) = lower($1) and p.name = $2
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubDeleteAllNodes(varchar(2049)) returns void as '
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
' LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubSetNodeAffiliation(bigint,varchar(2049),varchar(20)) returns void as '
declare
	_node_id alias for $1;
	_jid alias for $2;
	_affil alias for $3;
	_jid_id bigint;
	_exists int;
begin
	select jid_id into _jid_id from tig_pubsub_jids where lower(jid) = lower(_jid);
	if _jid_id is not null then
		select 1 into _exists from tig_pubsub_affiliations pa where pa.node_id = _node_id and pa.jid_id = _jid_id;
	end if;
	if _affil != ''none'' then
		if _jid_id is null then
			select TigPubSubEnsureJid(_jid) into _jid_id;
		end if;
		if _exists is not null then
			update tig_pubsub_affiliations set affiliation = _affil where node_id = _node_id and jid_id = _jid_id;
		else
			insert into tig_pubsub_affiliations (node_id, jid_id, affiliation)
				values (_node_id, _jid_id, _affil);
		end if;
	else
		if _exists is not null then
			delete from tig_pubsub_affiliations where node_id = _node_id and jid_id = _jid_id;
		end if;
	end if;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubDeleteNodeSubscription(bigint,varchar(2049)) returns void as $$
	delete from tig_pubsub_subscriptions where node_id = $1 and jid_id = (
		select jid_id from tig_pubsub_jids where lower(jid) = lower($2)
	)
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetUserAffiliations(varchar(2049),varchar(2049))
		returns table (node varchar(1024), affiliation varchar(20)) as $$
	select n.name, pa.affiliation from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_affiliations pa on pa.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = pa.jid_id
		where lower(pj.jid) = lower($2) and lower(sj.service_jid) = lower($1)
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetUserSubscriptions(varchar(2049),varchar(2049))
		returns table (node varchar(1024), subscription varchar(20), subscription_id varchar(40)) as $$
	select n.name, ps.subscription, ps.subscription_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_subscriptions ps on ps.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = ps.jid_id
		where lower(pj.jid) = lower($2) and lower(sj.service_jid) = lower($1)
$$ LANGUAGE SQL;
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

-- QUERY START:
create or replace function TigPubSubGetNodeMeta(_service_jid varchar(2049), _node_name varchar(1024)) returns table (
    node_id bigint,
    configuration text,
    creator varchar(2049),
    creation_date timestamp
) as $$
begin
    return query select n.node_id, n.configuration, cj.jid, n.creation_date
        from tig_pubsub_nodes n
		    inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		    inner join tig_pubsub_jids cj on cj.jid_id = n.creator_id
		    where lower(sj.service_jid) = lower(_service_jid) and n.name = _node_name;
end ;
$$ LANGUAGE 'plpgsql';
-- QUERY END: