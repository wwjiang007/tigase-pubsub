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

-- -----------------------------------------------------------------------------
-- Tables
-- -----------------------------------------------------------------------------

-- Table to store service jids
-- QUERY START:
create table if not exists tig_pubsub_service_jids (
	service_id bigserial,
	service_jid varchar(2049) not null,
	
	primary key ( service_id )
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_service_jids_service_jid')) is null) then
    create unique index tig_pubsub_service_jids_service_jid on tig_pubsub_service_jids ( service_jid );
end if;
end$$;
-- QUERY END:

-- QUERY START:
-- Table to store jids of node owners, subscribers and affiliates
create table if not exists tig_pubsub_jids (
	jid_id bigserial,
	jid varchar(2049) not null,

	primary key ( jid_id )
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_jids_jid')) is null) then
    create unique index tig_pubsub_jids_jid on tig_pubsub_jids ( jid );
end if;
end$$;
-- QUERY END:

-- QUERY START:
-- Table to store nodes configuration
create table if not exists tig_pubsub_nodes (
	node_id bigserial,
	service_id bigint not null references tig_pubsub_service_jids ( service_id ),
	name varchar(1024) not null,
	type int not null,
	title varchar(1000),
	description text,
	creator_id bigint references tig_pubsub_jids ( jid_id ),
	creation_date timestamp with time zone,
	configuration text,
	collection_id bigint references tig_pubsub_nodes ( node_id ),
	
	primary key ( node_id )
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_nodes_service_id')) is null) then
    create index tig_pubsub_nodes_service_id on tig_pubsub_nodes ( service_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_nodes_name')) is null) then
    create index tig_pubsub_nodes_name on tig_pubsub_nodes using hash ( name );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_nodes_service_id_name')) is null) then
    create unique index tig_pubsub_nodes_service_id_name on tig_pubsub_nodes ( service_id, name );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_nodes_collection_id')) is null) then
    create index tig_pubsub_nodes_collection_id on tig_pubsub_nodes ( collection_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
-- Table to store user nodes affiliations
create table if not exists tig_pubsub_affiliations (
	node_id bigint not null references tig_pubsub_nodes ( node_id ),
	jid_id bigint not null references tig_pubsub_jids ( jid_id ),
	affiliation varchar(20) not null,

	primary key ( node_id, jid_id )
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_affiliations_node_id')) is null) then
    create index tig_pubsub_affiliations_node_id on tig_pubsub_affiliations ( node_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_affiliations_jid_id')) is null) then
    create index tig_pubsub_affiliations_jid_id on tig_pubsub_affiliations ( jid_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
-- Table to store user nodes subscriptions
create table if not exists tig_pubsub_subscriptions (
	node_id bigint not null references tig_pubsub_nodes ( node_id ),
	jid_id bigint not null references tig_pubsub_jids ( jid_id ),
	subscription varchar(20) not null,
	subscription_id varchar(40) not null,

	primary key ( node_id, jid_id )
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_subscriptions_node_id')) is null) then
    create index tig_pubsub_subscriptions_node_id on tig_pubsub_subscriptions ( node_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_subscriptions_jid_id')) is null) then
    create index tig_pubsub_subscriptions_jid_id on tig_pubsub_jids ( jid_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
-- Table to store items
create table if not exists tig_pubsub_items (
	node_id bigint not null references tig_pubsub_nodes ( node_id ),
	id varchar(1024) not null,
	creation_date timestamp with time zone,
	publisher_id bigint references tig_pubsub_jids ( jid_id ),
	update_date timestamp with time zone,
	data text,

	primary key ( node_id, id )
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_items_node_id')) is null) then
    create index tig_pubsub_items_node_id on tig_pubsub_items ( node_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_pubsub_items_id')) is null) then
    create index tig_pubsub_items_id on tig_pubsub_items using hash ( id );
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
	select service_id into _service_id from tig_pubsub_service_jids where service_jid = _service_jid;
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
	select jid_id into _jid_id from tig_pubsub_jids where jid = _jid;
	if _jid_id is null then
		insert into tig_pubsub_jids (jid) values (_jid);
		select currval(''tig_pubsub_jids_jid_id_seq'') into _jid_id;
	end if;
	return _jid_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubCreateNode(varchar,varchar,int,varchar,text,bigint);
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubCreateNode(varchar(2049),varchar(1024),int,varchar(2049),text,bigint) returns bigint as '
declare
	_service_jid alias for $1;
	_node_name alias for $2;
	_node_type alias for $3;
	_node_creator alias for $4;
	_node_conf alias for $5;
	_collection_id alias for $6;
	_service_id bigint;
	_node_creator_id bigint;
	_node_id bigint;
begin
	select TigPubSubEnsureServiceJid(_service_jid) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;
	insert into tig_pubsub_nodes (service_id,name,"type",creator_id,configuration,collection_id)
		values (_service_id, _node_name, _node_type, _node_creator_id, _node_conf, _collection_id);
	select currval(''tig_pubsub_nodes_node_id_seq'') into _node_id;
	return _node_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubRemoveNode(bigint) returns void as '
declare
	_node_id alias for $1;
begin
	delete from tig_pubsub_items where node_id = _node_id;
	delete from tig_pubsub_subscriptions where node_id = _node_id;
	delete from tig_pubsub_affiliations where node_id = _node_id;
	delete from tig_pubsub_nodes where node_id = _node_id;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubDeleteItem(bigint,varchar(1024)) returns void as $$
	delete from tig_pubsub_items where node_id = $1 and id = $2
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetNodeId(varchar(2049),varchar(1024)) returns table (node_id bigint) as $$
	select n.node_id from tig_pubsub_nodes n 
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid = $1 and n.name = $2
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetNodeItemsIds(bigint) returns table (id varchar(1024)) as $$
	select id from tig_pubsub_items where node_id = $1 order by creation_date
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetAllNodes(varchar(2049)) returns table (name varchar(1024), node_id bigint) as $$
	select n.name, n.node_id from tig_pubsub_nodes n 
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id 
		where sj.service_jid = $1
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetRootNodes(varchar(2049)) returns table (name varchar(1024), node_id bigint) as $$
	select n.name, n.node_id from tig_pubsub_nodes n 
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id 
		where sj.service_jid = $1 and n.collection_id is null
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetChildNodes(varchar(2049),varchar(1024)) returns table (name varchar(1024), node_id bigint) as $$
	select n.name, n.node_id from tig_pubsub_nodes n 
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		inner join tig_pubsub_nodes p on p.node_id = n.collection_id and p.service_id = sj.service_id
		where sj.service_jid = $1 and p.name = $2
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubDeleteAllNodes(varchar(2049)) returns void as '
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
' LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubSetNodeConfiguration(bigint,text,bigint) returns void as $$
	update tig_pubsub_nodes set configuration = $2, collection_id = $3 where node_id = $1
$$ LANGUAGE SQL;
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
	select jid_id into _jid_id from tig_pubsub_jids where jid = _jid;
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
create or replace function TigPubSubGetNodeConfiguration(bigint) returns text as '
declare
	_node_id alias for $1;
	_config text;
begin
	select configuration into _config from tig_pubsub_nodes where node_id = _node_id;
	return _config;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetNodeAffiliations(bigint) returns table (jid varchar(2049),affiliation varchar(20)) as $$
	select pj.jid, pa.affiliation from tig_pubsub_affiliations pa 
		inner join tig_pubsub_jids pj on pa.jid_id = pj.jid_id
		where pa.node_id = $1
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetNodeSubscriptions(bigint) returns table (jid varchar(2049),subscription varchar(20),subscription_id varchar(40)) as $$
	select pj.jid, ps.subscription, ps.subscription_id 
		from tig_pubsub_subscriptions ps 
		inner join tig_pubsub_jids pj on ps.jid_id = pj.jid_id
		where ps.node_id = $1
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubSetNodeSubscription(bigint,varchar(2049),varchar(20),varchar(40)) returns void as '
declare
	_node_id alias for $1;
	_jid alias for $2;
	_subscr alias for $3;
	_subscr_id alias for $4;
	_jid_id bigint;
	_exists int;
begin
	select TigPubSubEnsureJid(_jid) into _jid_id;
	select 1 into _exists from tig_pubsub_subscriptions where node_id = _node_id and jid_id = _jid_id;
	if _exists is not null then
		update tig_pubsub_subscriptions set subscription = _subscr 
			where node_id = _node_id and jid_id = _jid_id;
	else
		insert into tig_pubsub_subscriptions (node_id,jid_id,subscription,subscription_id)
			values (_node_id,_jid_id,_subscr,_subscr_id);
	end if;
end;
' LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubDeleteNodeSubscription(bigint,varchar(2049)) returns void as $$
	delete from tig_pubsub_subscriptions where node_id = $1 and jid_id = (
		select jid_id from tig_pubsub_jids where jid = $2
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
		where pj.jid = $2 and sj.service_jid = $1
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function TigPubSubGetUserSubscriptions(varchar(2049),varchar(2049)) 
		returns table (node varchar(1024), subscription varchar(20), subscription_id varchar(40)) as $$
	select n.name, ps.subscription, ps.subscription_id from tig_pubsub_nodes n 
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_subscriptions ps on ps.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = ps.jid_id
		where pj.jid = $2 and sj.service_jid = $1
$$ LANGUAGE SQL;
-- QUERY END:

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
select TigSetComponentVersion('pubsub', '3.2.0');
-- QUERY END:
