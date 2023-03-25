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

delimiter ;

-- QUERY START:
alter table tig_pubsub_mam modify ts datetime(6) not null;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamQueryItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubMamUpdateItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubCreateNode;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubRemoveNode;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubWriteItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubDeleteAllNodes;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubSetNodeAffiliation;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubSetNodeSubscription;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubRemoveService;
-- QUERY END:


delimiter //

-- QUERY START:
create procedure TigPubSubMamQueryItem(_node_id bigint, _uuid varchar(36))
begin
select TigPubSubOrderedToUuid(pm.uuid), pm.ts, pm.data
from tig_pubsub_mam pm
where
        pm.node_id = _node_id and pm.uuid = TigPubSubUuidToOrdered(_uuid);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubMamUpdateItem(_node_id bigint, _uuid varchar(36), _item_data mediumtext charset utf8mb4)
begin
	update tig_pubsub_mam
        set data = _item_data
    where
        node_id = _node_id
        and uuid = TigPubSubUuidToOrdered(_uuid);
end //
-- QUERY END:


-- QUERY START:
create procedure TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024) charset utf8mb4 collate utf8mb4_bin, _node_type int,
	_node_creator varchar(2049), _node_conf mediumtext charset utf8mb4 collate utf8mb4_bin, _collection_id bigint, _ts timestamp(6), _domain varchar(1024), _createService int)
begin
	declare _service_id bigint;
	declare _node_creator_id bigint;
	declare _node_id bigint;

    -- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
    END;

	START TRANSACTION;
	call TigPubSubEnsureJid(_node_creator, _node_creator_id);
	COMMIT;

	START TRANSACTION;
	call TigPubSubEnsureServiceJid(_service_jid, _domain, _createService, _service_id);

	insert into tig_pubsub_nodes (service_id,name,name_sha1,`type`,creator_id, creation_date, configuration,collection_id)
			values (_service_id, _node_name, sha1(_node_name), _node_type, _node_creator_id, _ts, _node_conf, _collection_id);
	select LAST_INSERT_ID() into _node_id;
	select _node_id as node_id;
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeAffiliation(_node_id bigint, _jid varchar(2049), _affil varchar(20))
begin
    declare _jid_id bigint;
    declare _exists int;

    -- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;

    select jid_id into _jid_id from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_jid));
    if _jid_id is not null then
        select 1 into _exists from tig_pubsub_affiliations pa where pa.node_id = _node_id and pa.jid_id = _jid_id;
    end if;
    if _affil != 'none' then
        if _jid_id is null then
            call TigPubSubEnsureJid(_jid, _jid_id);
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
create procedure TigPubSubSetNodeSubscription(_node_id bigint, _jid varchar(2049),
                                              _subscr varchar(20), _subscr_id varchar(40))
begin
    declare _jid_id bigint;
    declare _exists int;

    -- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;

    call TigPubSubEnsureJid(_jid, _jid_id);
    select 1 into _exists from tig_pubsub_subscriptions where node_id = _node_id and jid_id = _jid_id;
    if _exists is not null then
        update tig_pubsub_subscriptions set subscription = _subscr
        where node_id = _node_id and jid_id = _jid_id;
    else
        insert into tig_pubsub_subscriptions (node_id,jid_id,subscription,subscription_id)
        values (_node_id,_jid_id,_subscr,_subscr_id);
    end if;

    COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubWriteItem(_node_id bigint, _item_id varchar(1024) charset utf8mb4 collate utf8mb4_bin, _publisher varchar(2049),
	 _item_data mediumtext charset utf8mb4, _ts timestamp(6), _uuid varchar(36))
begin
	declare _publisher_id bigint;

	START TRANSACTION;
	call TigPubSubEnsureJid(_publisher, _publisher_id);
	COMMIT;

	START TRANSACTION;
	insert into tig_pubsub_items (node_id, id_sha1, id, creation_date, update_date, publisher_id, data, uuid)
		values (_node_id, SHA1(_item_id), _item_id, _ts, _ts, _publisher_id, _item_data, TigPubSubUuidToOrdered(_uuid))
		on duplicate key update publisher_id = _publisher_id, data = _item_data, update_date = _ts, uuid = TigPubSubUuidToOrdered(_uuid);
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubRemoveService(_service_jid varchar(2049))
begin
    -- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
    END;
    
	START TRANSACTION;
    select * from tig_pubsub_service_jids where service_jid_sha1 = SHA1(LOWER(_service_jid)) for update;
    select * from tig_pubsub_jids where jid_sha1 = SHA1(LOWER(_service_jid)) for update;
	delete i
	    from tig_pubsub_items i
	    join tig_pubsub_nodes n on n.node_id = i.node_id
	    join tig_pubsub_service_jids s on n.service_id = s.service_id
	    where s.service_jid_sha1 = SHA1(LOWER(_service_jid));
	delete a
	    from tig_pubsub_affiliations a
	    join tig_pubsub_nodes n on n.node_id = a.node_id
	    join tig_pubsub_service_jids s on n.service_id = s.service_id
	    where s.service_jid_sha1 = SHA1(LOWER(_service_jid));
	delete sub
	    from tig_pubsub_subscriptions sub
	    join tig_pubsub_nodes n on n.node_id = sub.node_id
	    join tig_pubsub_service_jids s on n.service_id = s.service_id
	    where s.service_jid_sha1 = SHA1(LOWER(_service_jid));
	delete m
	    from tig_pubsub_mam m
	    join tig_pubsub_nodes n on n.node_id = m.node_id
	    join tig_pubsub_service_jids s on n.service_id = s.service_id
	    where s.service_jid_sha1 = SHA1(LOWER(_service_jid));
	delete n
	    from tig_pubsub_nodes n
	    join tig_pubsub_service_jids s on n.service_id = s.service_id
	    where s.service_jid_sha1 = SHA1(LOWER(_service_jid));
	delete from tig_pubsub_service_jids where service_jid_sha1 = SHA1(LOWER(_service_jid));
	delete a
	    from tig_pubsub_affiliations a
	    join tig_pubsub_jids j on j.jid_id = a.jid_id
	    where j.jid_sha1 = SHA1(LOWER(_service_jid));
	delete s
	    from tig_pubsub_subscriptions s
	    join tig_pubsub_jids j on j.jid_id = s.jid_id
	    where j.jid_sha1 = SHA1(LOWER(_service_jid));
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubRemoveNode(_node_id bigint)
begin
    -- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    delete from tig_pubsub_mam where node_id = _node_id;
    delete from tig_pubsub_items where node_id = _node_id;
    delete from tig_pubsub_subscriptions where node_id = _node_id;
    delete from tig_pubsub_affiliations where node_id = _node_id;
    delete from tig_pubsub_nodes where node_id = _node_id;
    COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteAllNodes(_service_jid varchar(2049))
begin
    declare _service_id bigint;
    -- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
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

delimiter ;