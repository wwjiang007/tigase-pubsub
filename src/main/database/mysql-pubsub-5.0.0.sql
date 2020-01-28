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
    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_pubsub_service_jids' and column_name = 'domain') then
        alter table tig_pubsub_service_jids add column domain varchar(1024);
        alter table tig_pubsub_service_jids add column domain_sha1 char(40) not null;
        alter table tig_pubsub_service_jids add column is_public int default 0;

        update tig_pubsub_service_jids set domain = CASE LOCATE('@', service_jid)
            WHEN 0 THEN service_jid
            ELSE SUBSTRING(service_jid, LOCATE('@', service_jid) + 1)
            end;

        alter table tig_pubsub_service_jids modify domain varchar(1024) not null;

        update tig_pubsub_service_jids set domain_sha1 = SHA1(domain);

        create index domain_sha1_is_public_idx on tig_pubsub_service_jids ( domain_sha1, is_public ) using hash;
    end if;

    if not exists (SELECT 1 FROM information_schema.statistics s1 WHERE s1.table_schema = database() AND s1.table_name = 'tig_pubsub_nodes' AND s1.index_name = 'collection_id_service_id') then
        create index collection_id_service_id on tig_pubsub_nodes ( collection_id, service_id );
        drop index service_id on tig_pubsub_nodes;
        drop index collection_id on tig_pubsub_nodes;
    end if;

    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_pubsub_items' and column_name = 'uuid') then
        alter table tig_pubsub_items add column uuid binary(16);
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
create function TigPubSubEnsureServiceJid(_service_jid varchar(2049), _domain varchar(1024), _createService int) returns bigint DETERMINISTIC
begin
	declare _service_id bigint;

	select service_id into _service_id from tig_pubsub_service_jids where service_jid_sha1 = SHA1(LOWER(_service_jid));
	if _service_id is null and _createService > 0 then
		insert into tig_pubsub_service_jids (service_jid, service_jid_sha1, domain, domain_sha1)
			values (_service_jid, SHA1(LOWER(_service_jid)), _domain, SHA1(LOWER(_domain)));
		select LAST_INSERT_ID() into _service_id;
	end if;

	return (_service_id);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024) charset utf8mb4 collate utf8mb4_bin, _node_type int,
	_node_creator varchar(2049), _node_conf text charset utf8mb4 collate utf8mb4_bin, _collection_id bigint, _ts timestamp(6), _domain varchar(1024), _createService int)
begin
	declare _service_id bigint;
	declare _node_creator_id bigint;
	declare _node_id bigint;
	declare _exists int;

-- Exception handler was intentionally removed as its behavior was wrong - we need exception on collision!

	START TRANSACTION;
	select TigPubSubEnsureServiceJid(_service_jid, _domain, _createService) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;

	insert into tig_pubsub_nodes (service_id,name,name_sha1,`type`,creator_id, creation_date, configuration,collection_id)
			values (_service_id, _node_name, sha1(_node_name), _node_type, _node_creator_id, _ts, _node_conf, _collection_id);
	select LAST_INSERT_ID() into _node_id;
	select _node_id as node_id;
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubRemoveService(_service_jid varchar(2049))
begin
	declare _service_id bigint;

-- Exception handler was intentionally removed as its behavior was wrong - we need exception on collision!

	START TRANSACTION;
	select service_id into _service_id from tig_pubsub_service_jids
		where service_jid_sha1 = SHA1(LOWER(_service_jid));
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

-- QUERY START:
create table if not exists tig_pubsub_mam (
    node_id bigint not null,
    uuid binary(16) not null,
    item_id_sha1 char(40),
    item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin,
    ts datetime not null,
    data mediumtext charset utf8mb4 collate utf8mb4_bin,

    primary key ( node_id, uuid ),

    index ( node_id, item_id_sha1, item_id(140) ),

    constraint
		foreign key ( node_id )
		references tig_pubsub_nodes ( node_id )
		match full
		on delete cascade
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubUuidToOrdered;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubOrderedToUuid;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubDeleteItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubWriteItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamAddItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItems;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItemPosition;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItemsCount;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubQueryItems;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubQueryItemPosition;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubQueryItemsCount;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetNodeItemsMeta;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetServices;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubCreateService;
-- QUERY END:

delimiter //

-- QUERY START:
create function TigPubSubUuidToOrdered(_uuid varchar(36)) returns binary(16) deterministic
    return unhex(concat(substr(_uuid, 15, 4), substr(_uuid, 10, 4), substr(_uuid, 1, 8), substr(_uuid, 20, 4), substr(_uuid, 25)));
-- QUERY END:

-- QUERY START:
create function TigPubSubOrderedToUuid(_uuid binary(16)) returns varchar(36) deterministic
begin
    declare hexed varchar(36);
    select hex(_uuid) into hexed;

    return concat(substr(hexed, 9, 8), '-', substr(hexed, 5, 4), '-', substr(hexed, 1, 4), '-', substr(hexed, 17, 4), '-', substr(hexed, 21));
end;
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteItem(_node_id bigint, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	delete from tig_pubsub_items where node_id = _node_id and id_sha1 = SHA1(_item_id) and id = _item_id;
	update tig_pubsub_mam set data = null where node_id = _node_id and item_id_sha1 = SHA1(_item_id) and item_id = _item_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubWriteItem(_node_id bigint, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin, _publisher varchar(2049),
	 _item_data mediumtext charset utf8mb4, _ts timestamp(6), _uuid varchar(36))
begin
	declare _publisher_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select TigPubSubEnsureJid(_publisher) into _publisher_id;
	insert into tig_pubsub_items (node_id, id_sha1, id, creation_date, update_date, publisher_id, data, uuid)
		values (_node_id, SHA1(_item_id), _item_id, _ts, _ts, _publisher_id, _item_data, TigPubSubUuidToOrdered(_uuid))
		on duplicate key update publisher_id = _publisher_id, data = _item_data, update_date = _ts, uuid = TigPubSubUuidToOrdered(_uuid);
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamAddItem(_node_id bigint, _uuid varchar(36), _ts timestamp(6),_item_data mediumtext charset utf8mb4, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	insert into tig_pubsub_mam (node_id, uuid, ts, data, item_id, item_id_sha1)
	    values (_node_id, TigPubSubUuidToOrdered(_uuid), _ts, _item_data, _item_id, SHA1(_item_id));
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItems(_node_id bigint, _since timestamp(6), _to timestamp(6), _limit int, _offset int)
begin
	select TigPubSubOrderedToUuid(pm.uuid), pm.ts, pm.data
        from tig_pubsub_mam pm
        where
            pm.node_id = _node_id
            and (_since is null or pm.ts >= _since)
            and (_to is null or pm.ts <= _to)
        order by pm.ts
        limit _limit offset _offset;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemPosition(_node_id bigint, _since timestamp(6), _to timestamp(6), _uuid varchar(36))
begin
    select x.position
        from (select @row_number := @row_number + 1 AS position, pm.uuid, pm.node_id
            from tig_pubsub_mam pm,
                (select @row_number := 0) as t
            where
                pm.node_id = _node_id
                and (_since is null or pm.ts >= _since)
                and (_to is null or pm.ts <= _to)
            order by pm.ts
        ) x where x.uuid = TigPubSubUuidToOrdered(_uuid);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemsCount(_node_id bigint, _since timestamp(6), _to timestamp(6))
begin
	select count(1)
            from tig_pubsub_mam pm
            where
                pm.node_id = _node_id
                and (_since is null or pm.ts >= _since)
                and (_to is null or pm.ts <= _to);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetItem(_node_id bigint, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	select `data`, pn.name, TigPubSubOrderedToUuid(pi.uuid)
		from tig_pubsub_items pi
		inner join tig_pubsub_nodes pn on pn.node_id = pi.node_id
		where pn.node_id = _node_id and pi.id_sha1 = SHA1(_item_id) and pi.id = _item_id;
end //
-- QUERY END:


-- QUERY START:
create procedure TigPubSubQueryItems(_nodes_ids text, _since timestamp(6), _to timestamp(6), _order int, _limit int, _offset int)
begin
    set @since = _since;
    set @to = _to;
    set @limit = _limit;
    set @offset = _offset;

	set @ts = 'creation_date';
	if _order = 2 then
	    set @ts = 'update_date';
	end if;

	set @query = CONCAT('select pn.name, pi.id, TigPubSubOrderedToUuid(pi.uuid), pi.data
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (', _nodes_ids, ')
            and (? is null or pi.', @ts, ' >= ?)
            and (? is null or pi.', @ts, ' <= ?)
        order by pi.', @ts, '
        limit ? offset ?;');

    prepare stmt from @query;
	execute stmt using @since, @since, @to, @to, @limit, @offset;
	deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubQueryItemPosition(_nodes_ids text, _since timestamp(6), _to timestamp(6), _order int, _nodeId bigint, _itemId varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
    set @since = _since;
    set @to = _to;
    set @nodeId = _nodeId;
    set @itemid = _itemId;

	set @ts = 'creation_date';
	if _order = 2 then
	    set @ts = 'update_date';
	end if;

	set @query = CONCAT('select x.position
        from (select @row_number := @row_number + 1 AS position, pi.id, pi.node_id
            from tig_pubsub_items pi,
                (select @row_number := 0) as t
            where
                pi.node_id in (', _nodes_ids, ')
                and (? is null or pi.', @ts, ' >= ?)
                and (? is null or pi.', @ts, ' <= ?)
            order by pi.', @ts, '
        ) x where x.node_id = ? and x.id = ?');

    prepare stmt from @query;
	execute stmt using @since, @since, @to, @to, @nodeId, @itemId;
	deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubQueryItemsCount(_nodes_ids text, _since timestamp(6), _to timestamp(6), _order int)
begin
    set @since = _since;
    set @to = _to;

	set @ts = 'creation_date';
	if _order = 2 then
	    set @ts = 'update_date';
	end if;

	set @query = CONCAT('select count(1)
            from tig_pubsub_items pi
            where
                pi.node_id in (', _nodes_ids, ')
                and (? is null or pi.', @ts, ' >= ?)
                and (? is null or pi.', @ts, ' <= ?)');

    prepare stmt from @query;
	execute stmt using @since, @since, @to, @to;
	deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsMeta(_node_id bigint)
begin
	select id, creation_date, update_date, uuid from tig_pubsub_items where node_id = _node_id order by creation_date;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetServices(_domain varchar(1024), _is_public int)
begin
	select service_jid, is_public from tig_pubsub_service_jids where domain_sha1 = SHA1(LOWER(_domain)) and (_is_public is null OR is_public = _is_public) and domain = _domain order by service_jid;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubCreateService(_service_jid varchar(2049), _domain varchar(1024), _is_public int)
begin
    insert into tig_pubsub_service_jids (service_jid, service_jid_sha1, domain, domain_sha1, is_public)
			values (_service_jid, SHA1(LOWER(_service_jid)), _domain, SHA1(LOWER(_domain)), _is_public);
end //
-- QUERY END:

delimiter ;