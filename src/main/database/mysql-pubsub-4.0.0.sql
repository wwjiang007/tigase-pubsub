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

-- QUERY START:
drop procedure if exists TigExecuteIf;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigExecuteIfLT8;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigExecuteIfGT8;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigExecuteIfNotGT8;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigExecuteIf(cond int, query text)
begin
set @s = (select if (
        cond > 0,
        query,
        'select 1'
    ));
prepare stmt from @s;
execute stmt;
deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigExecuteIfLT8(cond text, query text)
begin
  if exists (select 1 from information_schema.PLUGINS where PLUGIN_NAME = "InnoDB" and PLUGIN_TYPE_VERSION < 80000) then
    set @s = concat("set @cond = (", cond, ")");
    prepare stmt from @s;
    execute stmt;
    deallocate prepare stmt;
    set @s = (select if (
                           @cond > 0,
                           query,
                           'select 1'
                       ));
    prepare stmt from @s;
    execute stmt;
    deallocate prepare stmt;
  end if;
end //
-- QUERY END:

-- QUERY START:
create procedure TigExecuteIfGT8(cond text, query text)
begin
  if exists (select 1 from information_schema.PLUGINS where PLUGIN_NAME = "InnoDB" and PLUGIN_TYPE_VERSION >= 80000) then
    set @s = concat("set @cond = (", cond, ")");
    prepare stmt from @s;
    execute stmt;
    deallocate prepare stmt;
    set @s = (select if (
                           @cond > 0,
                           query,
                           'select 1'
                       ));
    prepare stmt from @s;
    execute stmt;
    deallocate prepare stmt;
  end if;
end //
-- QUERY END:

-- QUERY START:
create procedure TigExecuteIfNotGT8(cond text, query text)
begin
  if exists (select 1 from information_schema.PLUGINS where PLUGIN_NAME = "InnoDB" and PLUGIN_TYPE_VERSION >= 80000) then
    set @s = concat("set @cond = (", cond, ")");
    prepare stmt from @s;
    execute stmt;
    deallocate prepare stmt;
    set @s = (select if (
                           @cond <= 0,
                           query,
                           'select 1'
                       ));
    prepare stmt from @s;
    execute stmt;
    deallocate prepare stmt;
  end if;
end //
-- QUERY END:

delimiter ;


-- QUERY START:
call TigExecuteIfLT8(
    "SELECT count(*)
      FROM information_schema.statistics s1
        INNER JOIN information_schema.statistics s2
          ON s1.table_schema = s2.table_schema AND s1.table_name = s2.table_name AND s1.index_name = s2.index_name
        JOIN (SELECT @@GLOBAL.innodb_large_prefix AS val) x
      WHERE
        s1.table_schema = database()
        AND s1.table_name = 'tig_pubsub_items'
        AND s1.column_name = 'node_id'
        AND s2.column_name = 'id'
        AND ((s2.sub_part = 255
              AND x.val = 0) OR (s2.sub_part <> 255 AND x.val = 1))
    ",
    "drop index `node_id` on tig_pubsub_items"
);
-- QUERY END:

-- QUERY START:
call TigExecuteIfGT8(
      "SELECT count(*)
      FROM information_schema.statistics s1
        INNER JOIN information_schema.statistics s2
          ON s1.table_schema = s2.table_schema AND s1.table_name = s2.table_name AND s1.index_name = s2.index_name
      WHERE
        s1.table_schema = database()
        AND s1.table_name = 'tig_pubsub_items'
        AND s1.column_name = 'node_id'
        AND s2.column_name = 'id'
        AND s2.sub_part <> 255
    ",
    "drop index `node_id` on tig_pubsub_items"
);
-- QUERY END:

-- QUERY START:
alter table tig_pubsub_items
    modify `id` varchar(1000) character set utf8mb4 collate utf8mb4_bin,
    modify `data` mediumtext character set utf8mb4 collate utf8mb4_bin;
-- QUERY END:

-- QUERY START:
call TigExecuteIfLT8(
    "SELECT 1
      FROM dual
      WHERE (SELECT @@GLOBAL.innodb_large_prefix val
             FROM dual
             WHERE (SELECT count(*)
                    FROM information_schema.statistics s1
                      INNER JOIN information_schema.statistics s2
                        ON s1.table_schema = s2.table_schema AND s1.table_name = s2.table_name AND
                           s1.index_name = s2.index_name
                      JOIN (SELECT @@GLOBAL.innodb_large_prefix AS val) x
                    WHERE
                      s1.table_schema = database()
                      AND s1.table_name = 'tig_pubsub_items'
                      AND s1.column_name = 'node_id'
                      AND s2.column_name = 'id') = 0) = 0
    ",
    "create index node_id_id on tig_pubsub_items ( node_id, id(190) ) using hash;"
);
-- QUERY END:

-- QUERY START:
call TigExecuteIfNotGT8(
    "SELECT count(*)
      FROM information_schema.statistics s1
        INNER JOIN information_schema.statistics s2
          ON s1.table_schema = s2.table_schema AND s1.table_name = s2.table_name AND s1.index_name = s2.index_name
      WHERE
        s1.table_schema = database()
        AND s1.table_name = 'tig_pubsub_items'
        AND s1.column_name = 'node_id'
        AND s2.column_name = 'id'
    ",
    "create index node_id_id on tig_pubsub_items ( node_id, id(255) ) using hash;"
);
-- QUERY END:

-- QUERY START:
call TigExecuteIf(
    (select count(1) from information_schema.statistics where table_schema = database() and table_name = 'tig_pubsub_nodes' and index_name = 'name'),
    "drop index `name` on tig_pubsub_nodes"
);
-- QUERY END:
-- QUERY START:
call TigExecuteIf(
    (select count(1) from information_schema.statistics where table_schema = database() and table_name = 'tig_pubsub_nodes' and index_name = 'service_id_2'),
    "drop index `service_id_2` on tig_pubsub_nodes"
);
-- QUERY END:

-- QUERY START:
alter table tig_pubsub_nodes
    modify `name` varchar(1024) character set utf8mb4 collate utf8mb4_bin not null,
    modify title varchar(1000) character set utf8mb4 collate utf8mb4_bin,
    modify description mediumtext character set utf8mb4 collate utf8mb4_bin,
    modify configuration mediumtext character set utf8mb4 collate utf8mb4_bin;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubEnsureServiceJid;
-- QUERY END:

-- QUERY START:
drop function if exists TigPubSubEnsureJid;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetNodeId;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetAllNodes;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetRootNodes;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetChildNodes;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubDeleteAllNodes;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubSetNodeAffiliation;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubDeleteNodeSubscription;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetUserAffiliations;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetUserSubscriptions;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubCreateNode;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubRemoveService;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetNodeMeta;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItems;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItemPosition;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItemsCount;
-- QUERY END:

delimiter //

-- QUERY START:
create function TigPubSubEnsureServiceJid(_service_jid varchar(2049)) returns bigint DETERMINISTIC
begin
	declare _service_id bigint;

	select service_id into _service_id from tig_pubsub_service_jids where service_jid_sha1 = SHA1(LOWER(_service_jid));
	if _service_id is null then
		insert into tig_pubsub_service_jids (service_jid, service_jid_sha1)
			values (_service_jid, SHA1(LOWER(_service_jid)));
		select LAST_INSERT_ID() into _service_id;
	end if;

	return (_service_id);
end //
-- QUERY END:

-- QUERY START:
create function TigPubSubEnsureJid(_jid varchar(2049)) returns bigint DETERMINISTIC
begin
	declare _jid_id bigint;

	select jid_id into _jid_id from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_jid));
	if _jid_id is null then
		insert into tig_pubsub_jids (jid, jid_sha1)
			values (_jid, SHA1(LOWER(_jid)))
			on duplicate key update jid_id = LAST_INSERT_ID(jid_id);
		select LAST_INSERT_ID() into _jid_id;
	end if;

	return (_jid_id);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeId(_service_jid varchar(2049), _node_name varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	select n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(LOWER(_service_jid)) and n.name_sha1 = SHA1(_node_name)
			and n.name = _node_name;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetAllNodes(_service_jid varchar(2049))
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(LOWER(_service_jid));
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetRootNodes(_service_jid varchar(2049))
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(LOWER(_service_jid)) and n.collection_id is null;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetChildNodes(_service_jid varchar(2049),_node_name varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		inner join tig_pubsub_nodes p on p.node_id = n.collection_id and p.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(LOWER(_service_jid)) and p.name_sha1 = SHA1(_node_name)
			and p.name = _node_name;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteAllNodes(_service_jid varchar(2049))
begin
	declare _service_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

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

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeAffiliation(_node_id bigint, _jid varchar(2049), _affil varchar(20))
begin
	declare _jid_id bigint;
	declare _exists int;

	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select jid_id into _jid_id from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_jid));
	if _jid_id is not null then
		select 1 into _exists from tig_pubsub_affiliations pa where pa.node_id = _node_id and pa.jid_id = _jid_id;
	end if;
	if _affil != 'none' then
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

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteNodeSubscription(_node_id bigint, _jid varchar(2049))
begin
	delete from tig_pubsub_subscriptions where node_id = _node_id and jid_id = (
		select jid_id from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_jid))
	);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetUserAffiliations(_service_jid varchar(2049), _jid varchar(2049))
begin
	select n.name, pa.affiliation from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_affiliations pa on pa.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = pa.jid_id
		where pj.jid_sha1 = SHA1(LOWER(_jid)) and sj.service_jid_sha1 = SHA1(LOWER(_service_jid));
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetUserSubscriptions(_service_jid varchar(2049), _jid varchar(2049))
begin
	select n.name, ps.subscription, ps.subscription_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_subscriptions ps on ps.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = ps.jid_id
		where pj.jid_sha1 = SHA1(LOWER(_jid)) and sj.service_jid_sha1 = SHA1(LOWER(_service_jid));
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024) charset utf8mb4 collate utf8mb4_bin, _node_type int,
	_node_creator varchar(2049), _node_conf text charset utf8mb4 collate utf8mb4_bin, _collection_id bigint, _ts timestamp(6))
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
	select TigPubSubEnsureServiceJid(_service_jid) into _service_id;
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
create procedure TigPubSubRemoveService(_service_jid varchar(2049))
begin
	declare _service_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

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

-- QUERY START:
create procedure TigPubSubGetNodeMeta(_service_jid varchar(2049), _node_name varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	select n.node_id, n.configuration, cj.jid, n.creation_date
	from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		inner join tig_pubsub_jids cj on cj.jid_id = n.creator_id
		where sj.service_jid_sha1 = SHA1(LOWER(_service_jid)) and n.name_sha1 = SHA1(_node_name)
			and n.name = _node_name;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItems(_nodes_ids text, _since timestamp(6), _to timestamp(6), _publisher varchar(2049), _order int, _limit int, _offset int)
begin
    set @since = _since;
    set @to = _to;
    set @publisherId = null;
    set @limit = _limit;
    set @offset = _offset;

    if _publisher is not null then
        select jid_id into @publisherId from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_publisher));
    end if;

	set @ts = 'creation_date';
	if _order = 1 then
	    set @ts = 'update_date';
	end if;

	set @query = CONCAT('select pn.name, pi.node_id, pi.id, pi.', @ts, ', pi.data
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (', _nodes_ids, ')
            and (? is null or pi.', @ts, ' >= ?)
            and (? is null or pi.', @ts, ' <= ?)
            and (? is null or pi.publisher_id = ?)
        order by pi.', @ts, '
        limit ? offset ?;');

    prepare stmt from @query;
	execute stmt using @since, @since, @to, @to, @publisherId, @publisherId, @limit, @offset;
	deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemPosition(_nodes_ids text, _since timestamp(6), _to timestamp(6), _publisher varchar(2049), _order int, _nodeId bigint, _itemId varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
    set @since = _since;
    set @to = _to;
    set @publisherId = null;
    set @nodeId = _nodeId;
    set @itemid = _itemId;

    if _publisher is not null then
        select jid_id into @publisherId from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_publisher));
    end if;

	set @ts = 'creation_date';
	if _order = 1 then
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
                and (? is null or pi.publisher_id = ?)
            order by pi.', @ts, '
        ) x where x.node_id = ? and x.id = ?');

    prepare stmt from @query;
	execute stmt using @since, @since, @to, @to, @publisherId, @publisherId, @nodeId, @itemId;
	deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamQueryItemsCount(_nodes_ids text, _since timestamp(6), _to timestamp(6), _publisher varchar(2049), _order int)
begin
    set @since = _since;
    set @to = _to;
    set @publisherId = null;

    if _publisher is not null then
        select jid_id into @publisherId from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_publisher));
    end if;

	set @ts = 'creation_date';
	if _order = 1 then
	    set @ts = 'update_date';
	end if;

	set @query = CONCAT('select count(1)
            from tig_pubsub_items pi
            where
                pi.node_id in (', _nodes_ids, ')
                and (? is null or pi.', @ts, ' >= ?)
                and (? is null or pi.', @ts, ' <= ?)
                and (? is null or pi.publisher_id = ?)');

    prepare stmt from @query;
	execute stmt using @since, @since, @to, @to, @publisherId, @publisherId;
	deallocate prepare stmt;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
update tig_pubsub_service_jids
    set service_jid_sha1 = SHA1(LOWER(service_jid))
    where lower(service_jid) <> service_jid;
-- QUERY END:

-- QUERY START:
update tig_pubsub_jids
    set jid_sha1 = SHA1(LOWER(jid))
    where lower(jid) <> jid;
-- QUERY END:

-- QUERY START:
alter table tig_pubsub_items modify update_date timestamp(6) null default null;
-- QUERY END:

-- QUERY START:
alter table tig_pubsub_items modify creation_date timestamp(6) null default null;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubWriteItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubSetNodeConfiguration;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubDeleteItem;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigPubSubWriteItem(_node_id bigint, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin, _publisher varchar(2049),
	 _item_data mediumtext charset utf8mb4, _ts timestamp(6))
begin
	declare _publisher_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select TigPubSubEnsureJid(_publisher) into _publisher_id;
	insert into tig_pubsub_items (node_id, id_sha1, id, creation_date, update_date, publisher_id, data)
		values (_node_id, SHA1(_item_id), _item_id, _ts, _ts, _publisher_id, _item_data)
		on duplicate key update publisher_id = _publisher_id, data = _item_data, update_date = _ts;
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeConfiguration(_node_id bigint, _node_conf mediumtext charset utf8mb4 collate utf8mb4_bin, _collection_id bigint)
begin
	update tig_pubsub_nodes set configuration = _node_conf, collection_id = _collection_id where node_id = _node_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetItem(_node_id bigint, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	select `data`, p.jid, creation_date, update_date
		from tig_pubsub_items pi
		inner join tig_pubsub_jids p on p.jid_id = pi.publisher_id
		where node_id = _node_id and id_sha1 = SHA1(_item_id) and id = _item_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteItem(_node_id bigint, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin)
begin
	delete from tig_pubsub_items where node_id = _node_id and id_sha1 = SHA1(_item_id) and id = _item_id;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
drop procedure if exists TigPubSubCountNodes;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigPubSubCountNodes(_service_jid varchar(2049))
begin
	select count(1)
	from tig_pubsub_nodes n
	where
	    _service_jid is null
	    or n.service_id = (
	        select sj.service_id
	        from tig_pubsub_service_jids sj
		    where sj.service_jid_sha1 = SHA1(lower(_service_jid))
		);
end //
-- QUERY END:

delimiter ;

-- QUERY START:
call TigSetComponentVersion('pubsub', '4.0.0');
-- QUERY END:
