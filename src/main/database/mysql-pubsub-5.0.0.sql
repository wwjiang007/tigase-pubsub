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
drop procedure if exists TigPubSubUpgrade;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigPubSubUpgrade()
begin
    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_pubsub_service_jids' and column_name = 'component_name') then
        alter table tig_pubsub_service_jids add column component_name varchar(190);

        update tig_pubsub_service_jids set component_name = (select name from (
            select LEFT(service_jid, pos - 1) as name
            from (
                select service_jid, LOCATE('.', service_jid) as pos
                from tig_pubsub_service_jids where service_jid not like '%@%' having pos > 0
            ) x group by LEFT(service_jid, pos - 1)
            union
            select 'pubsub' as name
        ) y limit 1) where component_name is null;

        alter table tig_pubsub_service_jids modify component_name varchar(190) not null;

        create index component_name_idx on tig_pubsub_service_jids ( component_name ) using hash;
    end if;

    if not exists (SELECT 1 FROM information_schema.statistics s1 WHERE s1.table_schema = database() AND s1.table_name = 'tig_pubsub_nodes' AND s1.index_name = 'collection_id_service_id') then
        create index collection_id_service_id on tig_pubsub_nodes ( collection_id, service_id );
        drop index service_id on tig_pubsub_nodes;
        drop index collection_id on tig_pubsub_nodes;
    end if;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
call TigPubSubUpgrade();
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubUpgrade;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubEnsureServiceJid;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubCreateNode;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubRemoveService;
-- QUERY END:

delimiter //

-- QUERY START:
create function TigPubSubEnsureServiceJid(_service_jid varchar(2049), _component_name varchar(190)) returns bigint DETERMINISTIC
begin
	declare _service_id bigint;

	select service_id into _service_id from tig_pubsub_service_jids where service_jid_sha1 = SHA1(LOWER(_service_jid));
	if _service_id is null then
		insert into tig_pubsub_service_jids (service_jid, service_jid_sha1, component_name)
			values (_service_jid, SHA1(LOWER(_service_jid)), _component_name);
		select LAST_INSERT_ID() into _service_id;
	end if;

	return (_service_id);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024) charset utf8mb4 collate utf8mb4_bin, _node_type int,
	_node_creator varchar(2049), _node_conf text charset utf8mb4 collate utf8mb4_bin, _collection_id bigint, _ts timestamp(6), _component_name varchar(190))
begin
	declare _service_id bigint;
	declare _node_creator_id bigint;
	declare _node_id bigint;
	declare _exists int;

	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		select node_id from tig_pubsub_nodes
			where service_id = (
			        select service_id from tig_pubsub_service_jids where service_jid_sha1 = SHA1(LOWER(_service_id))
			    )
			    and name_sha1 = sha1(_node_name) and name = _node_name;
	END;

	START TRANSACTION;
	select TigPubSubEnsureServiceJid(_service_jid, _component_name) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;

	select node_id into _exists from tig_pubsub_nodes where service_id = _service_id and name_sha1 = sha1(_node_name) and name = _node_name;
	if _exists is not null then
		select _exists as node_id;
	else
		insert into tig_pubsub_nodes (service_id,name,name_sha1,`type`,creator_id, creation_date, configuration,collection_id)
			values (_service_id, _node_name, sha1(_node_name), _node_type, _node_creator_id, _ts, _node_conf, _collection_id);
		select LAST_INSERT_ID() into _node_id;
		select _node_id as node_id;
	end if;

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubRemoveService(_service_jid varchar(2049), _component_name varchar(190))
begin
	declare _service_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select service_id into _service_id from tig_pubsub_service_jids
		where service_jid_sha1 = SHA1(LOWER(_service_jid)) and component_name = _component_name;
	delete from tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_nodes where service_id = _service_id;
	delete from tig_pubsub_service_jids where service_id = _service_id;
	delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = SHA1(LOWER(_service_jid)));
	delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = SHA1(LOWER(_service_jid)));
	COMMIT;
end //
-- QUERY END:

delimiter ;
