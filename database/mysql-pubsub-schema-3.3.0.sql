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

source database/mysql-pubsub-schema-3.2.0.sql;

-- LOAD FILE: database/mysql-pubsub-schema-3.2.0.sql

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
	declare _service_jid_sha1 char(40);

	select SHA1(LOWER(_service_jid)) into _service_jid_sha1;
	select service_id into _service_id from tig_pubsub_service_jids where service_jid_sha1 = _service_jid_sha1;
	if _service_id is null then
		insert into tig_pubsub_service_jids (service_jid, service_jid_sha1)
			values (_service_jid, _service_jid_sha1);
		select LAST_INSERT_ID() into _service_id;
	end if;

	return (_service_id);
end //
-- QUERY END:

-- QUERY START:
create function TigPubSubEnsureJid(_jid varchar(2049)) returns bigint DETERMINISTIC
begin
	declare _jid_id bigint;
	declare _jid_sha1 char(40);

	select SHA1(LOWER(_jid)) into _jid_sha1;
	select jid_id into _jid_id from tig_pubsub_jids where jid_sha1 = _jid_sha1;
	if _jid_id is null then
		insert into tig_pubsub_jids (jid, jid_sha1)
			values (_jid, _jid_sha1)
			on duplicate key update jid_id = LAST_INSERT_ID(jid_id);
		select LAST_INSERT_ID() into _jid_id;
	end if;

	return (_jid_id);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeId(_service_jid varchar(2049), _node_name varchar(1024))
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
create procedure TigPubSubGetChildNodes(_service_jid varchar(2049),_node_name varchar(1024))
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
create procedure TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024), _node_type int,
	_node_creator varchar(2049), _node_conf text, _collection_id bigint)
begin
	declare _service_id bigint;
	declare _node_creator_id bigint;
	declare _node_id bigint;
	declare _exists int;

	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		select node_id from tig_pubsub_nodes
			where name = _node_name and service_id = (
			        select service_id from tig_pubsub_service_jids where service_jid_sha1 = SHA1(LOWER(_service_id)));
	END;

	START TRANSACTION;
	select TigPubSubEnsureServiceJid(_service_jid) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;

	select node_id into _exists from tig_pubsub_nodes where name = _node_name and service_id = _service_id;
	if _exists is not null then
		select _exists as node_id;
	else
		insert into tig_pubsub_nodes (service_id,name,name_sha1,`type`,creator_id, creation_date, configuration,collection_id)
			values (_service_id, _node_name, SHA1(_node_name), _node_type, _node_creator_id, now(), _node_conf, _collection_id);
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
create procedure TigPubSubGetNodeMeta(_service_jid varchar(2049), _node_name varchar(1024))
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
create procedure TigPubSubMamQueryItems(_nodes_ids text, _since timestamp , _to timestamp, _publisher varchar(2049), _order int, _limit int, _offset int)
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
create procedure TigPubSubMamQueryItemPosition(_nodes_ids text, _since timestamp , _to timestamp, _publisher varchar(2049), _order int, _nodeId bigint, _itemId varchar(1024))
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
create procedure TigPubSubMamQueryItemsCount(_nodes_ids text, _since timestamp , _to timestamp, _publisher varchar(2049), _order int)
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