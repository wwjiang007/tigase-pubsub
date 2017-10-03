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

-- LOAD FILE: database/sqlserver-pubsub-schema-3.2.0.sql

-- QUERY START:
SET QUOTED_IDENTIFIER ON
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubEnsureServiceJid')
	DROP PROCEDURE TigPubSubEnsureServiceJid
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubEnsureServiceJid
	@_service_jid nvarchar(2049),
	@_service_id bigint OUTPUT
AS
begin
	declare @_service_jid_sha1 varbinary(20);

		set @_service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid));
		select @_service_id=service_id from tig_pubsub_service_jids
			where service_jid_sha1 = @_service_jid_sha1;
		if @_service_id is null
		begin
	BEGIN TRY
			insert into tig_pubsub_service_jids (service_jid,service_jid_sha1)
				select @_service_jid, @_service_jid_sha1 where not exists(
							select 1 from tig_pubsub_service_jids where service_jid_sha1 = @_service_jid_sha1);
			set @_service_id = @@IDENTITY
			END TRY
			BEGIN CATCH
					IF ERROR_NUMBER() = 2627
						select @_service_id=service_id from tig_pubsub_service_jids
							where service_jid_sha1 = @_service_jid_sha1;
					ELSE
						declare @ErrorMessage nvarchar(max), @ErrorSeverity int, @ErrorState int;
						select @ErrorMessage = ERROR_MESSAGE() + ' Line ' + cast(ERROR_LINE() as nvarchar(5)), @ErrorSeverity = ERROR_SEVERITY(), @ErrorState = ERROR_STATE();
						raiserror (@ErrorMessage, @ErrorSeverity, @ErrorState);
			END CATCH
		end
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubEnsureJid')
	DROP PROCEDURE TigPubSubEnsureJid
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubEnsureJid
	@_jid nvarchar(2049),
	@_jid_id bigint OUTPUT
AS
begin
	declare @_jid_sha1 varbinary(20);

		set @_jid_sha1 = HASHBYTES('SHA1', LOWER(@_jid));
		select @_jid_id=jid_id from tig_pubsub_jids
			where jid_sha1 = @_jid_sha1;
		if @_jid_id is null
		begin
			BEGIN TRY
			insert into tig_pubsub_jids (jid,jid_sha1)
				select @_jid, @_jid_sha1 where not exists(
							select 1 from tig_pubsub_jids where jid_sha1 = @_jid_sha1);
			set @_jid_id = @@IDENTITY
			END TRY
			BEGIN CATCH
					IF ERROR_NUMBER() = 2627
						select @_jid_id=jid_id from tig_pubsub_jids
							where jid_sha1 = @_jid_sha1;
					ELSE
						declare @ErrorMessage nvarchar(max), @ErrorSeverity int, @ErrorState int;
						select @ErrorMessage = ERROR_MESSAGE() + ' Line ' + cast(ERROR_LINE() as nvarchar(5)), @ErrorSeverity = ERROR_SEVERITY(), @ErrorState = ERROR_STATE();
						raiserror (@ErrorMessage, @ErrorSeverity, @ErrorState);
			END CATCH
		end
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeId')
	DROP PROCEDURE TigPubSubGetNodeId
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetNodeId
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(1024)
AS
begin
	select n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid))
			and n.name_sha1 = HASHBYTES('SHA1', @_node_name)
			and n.name = @_node_name;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetAllNodes')
	DROP PROCEDURE TigPubSubGetAllNodes
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetAllNodes
	@_service_jid nvarchar(2049)
AS
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid));
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetRootNodes')
	DROP PROCEDURE TigPubSubGetRootNodes
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetRootNodes
	@_service_jid nvarchar(2049)
AS
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid))
			and n.collection_id is null;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetChildNodes')
	DROP PROCEDURE TigPubSubGetChildNodes
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetChildNodes
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(1024)
AS
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		inner join tig_pubsub_nodes p on p.node_id = n.collection_id and p.service_id = sj.service_id
		where sj.service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid)) and p.name_sha1 = HASHBYTES('SHA1', @_node_name)
			and p.name = @_node_name;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteAllNodes')
	DROP PROCEDURE TigPubSubDeleteAllNodes
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubDeleteAllNodes
	@_service_jid nvarchar(2049)
AS
begin
	declare @_service_id bigint;

	select @_service_id=service_id from tig_pubsub_service_jids where service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid));

	delete from dbo.tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from dbo.tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);;
	delete from dbo.tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from dbo.tig_pubsub_nodes where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubSetNodeAffiliation')
	DROP PROCEDURE TigPubSubSetNodeAffiliation
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubSetNodeAffiliation
	@_node_id bigint,
	@_jid nvarchar(2049),
	@_affil nvarchar(20)
AS
begin
	declare @_jid_id bigint;
	declare @_exists int;

	select @_jid_id = jid_id from tig_pubsub_jids where jid_sha1 = HASHBYTES('SHA1', LOWER(@_jid));
	if @_jid_id is not null
		select @_exists = 1 from tig_pubsub_affiliations where node_id = @_node_id and jid_id = @_jid_id;
	if @_affil != 'none'
	begin
			if @_jid_id is null
				exec TigPubSubEnsureJid @_jid=@_jid, @_jid_id=@_jid_id output;
			if @_exists is not null
				update tig_pubsub_affiliations set affiliation = @_affil where node_id = @_node_id and jid_id = @_jid_id;
			else
				BEGIN TRY
					insert into tig_pubsub_affiliations (node_id, jid_id, affiliation)
					select @_node_id, @_jid_id, @_affil where not exists(
						select 1 from tig_pubsub_affiliations where node_id = @_node_id AND jid_id = @_jid_id);
				END TRY
				BEGIN CATCH
						IF ERROR_NUMBER() <> 2627
						declare @ErrorMessage nvarchar(max), @ErrorSeverity int, @ErrorState int;
						select @ErrorMessage = ERROR_MESSAGE() + ' Line ' + cast(ERROR_LINE() as nvarchar(5)), @ErrorSeverity = ERROR_SEVERITY(), @ErrorState = ERROR_STATE();
						raiserror (@ErrorMessage, @ErrorSeverity, @ErrorState);
				END CATCH
		end
	else
	begin
		if @_exists is not null
			delete from tig_pubsub_affiliations where node_id = @_node_id and jid_id = @_jid_id;
	end
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteNodeSubscription')
	DROP PROCEDURE TigPubSubDeleteNodeSubscription
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubDeleteNodeSubscription
	@_node_id bigint,
	@_jid nvarchar(2049)
AS
begin
	delete from tig_pubsub_subscriptions where node_id = @_node_id and jid_id = (
		select jid_id from tig_pubsub_jids where jid_sha1 = HASHBYTES('SHA1', LOWER(@_jid)));
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetUserAffiliations')
	DROP PROCEDURE TigPubSubGetUserAffiliations
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetUserAffiliations
	@_service_jid nvarchar(2049),
	@_jid nvarchar(2049)
AS
begin
	select n.name, pa.affiliation from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_affiliations pa on pa.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = pa.jid_id
		where pj.jid_sha1 = HASHBYTES('SHA1',LOWER(@_jid)) and sj.service_jid_sha1 = HASHBYTES('SHA1',LOWER(@_service_jid));
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetUserSubscriptions')
	DROP PROCEDURE TigPubSubGetUserSubscriptions
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetUserSubscriptions
	@_service_jid nvarchar(2049),
	@_jid nvarchar(2049)
AS
begin
	select n.name, ps.subscription, ps.subscription_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_subscriptions ps on ps.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = ps.jid_id
		where pj.jid_sha1 = HASHBYTES('SHA1',LOWER(@_jid)) and sj.service_jid_sha1 = HASHBYTES('SHA1',LOWER(@_service_jid));
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubRemoveService')
	DROP PROCEDURE TigPubSubRemoveService
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubRemoveService
	@_service_jid nvarchar(2049)
AS
begin
	declare @_service_id bigint;

	select @_service_id=service_id from tig_pubsub_service_jids where service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid));

	delete from dbo.tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from dbo.tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);;
	delete from dbo.tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from dbo.tig_pubsub_nodes where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = @_service_id);
	delete from tig_pubsub_service_jids where service_id = @_service_id;
	delete from tig_pubsub_affiliations where jid_id in (
		select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid)));
	delete from tig_pubsub_subscriptions where jid_id in (
		select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid)));
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeMeta')
	DROP PROCEDURE TigPubSubGetNodeMeta
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetNodeMeta
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(1024)
AS
begin
    select n.node_id, n.configuration, cj.jid, n.creation_date
        from tig_pubsub_nodes n
		    inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		    inner join tig_pubsub_jids cj on cj.jid_id = n.creator_id
		    where sj.service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid)) and n.name_sha1 = HASHBYTES('SHA1', @_node_name)
		        and n.name = @_node_name;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubMamQueryItems')
	DROP PROCEDURE TigPubSubMamQueryItems
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubMamQueryItems
	@_nodes_ids nvarchar(max),
	@_since datetime,
	@_to datetime,
	@_publisher nvarchar(2049),
	@_order int,
	@_limit int,
	@_offset int
AS
begin
	declare
	    @_publisherId bigint,
	    @_ts nvarchar(20),
		@params_def nvarchar(max),
		@query_sql nvarchar(max);

    if @_publisher is not null
        begin
        select @_publisherId=jid_id
        from tig_pubsub_jids
        where jid_sha1 = HASHBYTES('SHA1', LOWER(@_publisher));
        end

    set @_ts = N'creation_date';
    if @_order = 1
        begin
        set @_ts = N'update_date';
        end

	set @params_def = N'@_since datetime, @_to datetime, @_publisherId bigint,  @_offset int, @_limit int';

	set @query_sql = N';with results_cte as(
	    select pn.name, pi.node_id, pi.id, pi.' + @_ts + ', pi.data, row_number() over (order by pi.' + @_ts + ') as row_num
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (' + @_nodes_ids + ')
            and (@_since is null or pi.' + @_ts + ' >= @_since)
            and (@_to is null or pi.' + @_ts + ' <= @_to)
            and (@_publisherId is null or pi.publisher_id = @_publisherId)
    ) select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num';

	execute sp_executesql @query_sql, @params_def, @_since=@_since, @_to=@_to, @_publisherId=@_publisherId, @_offset=@_offset, @_limit=@_limit;

end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubMamQueryItemPosition')
	DROP PROCEDURE TigPubSubMamQueryItemPosition
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubMamQueryItemPosition
	@_nodes_ids nvarchar(max),
	@_since datetime,
	@_to datetime,
	@_publisher nvarchar(2049),
	@_order int,
	@_node_id bigint,
	@_item_id nvarchar(1024)
AS
begin
	declare
	    @_publisherId bigint,
	    @_ts nvarchar(20),
		@params_def nvarchar(max),
		@query_sql nvarchar(max);

    if @_publisher is not null
        begin
        select @_publisherId=jid_id
        from tig_pubsub_jids
        where jid_sha1 = HASHBYTES('SHA1', LOWER(@_publisher));
        end

    set @_ts = N'creation_date';
    if @_order = 1
        begin
        set @_ts = N'update_date';
        end

	set @params_def = N'@_since datetime, @_to datetime, @_publisherId bigint,  @_node_id bigint, @_item_id nvarchar(1024)';

	set @query_sql = N';with results_cte as(
	    select pi.node_id, pi.id, row_number() over (order by pi.' + @_ts + ') as position
        from tig_pubsub_items pi
        where
            pi.node_id in (' + @_nodes_ids + ')
            and (@_since is null or pi.' + @_ts + ' >= @_since)
            and (@_to is null or pi.' + @_ts + ' <= @_to)
            and (@_publisherId is null or pi.publisher_id = @_publisherId)
    ) select position from results_cte where node_id = @_node_id and id = @_item_id';

	execute sp_executesql @query_sql, @params_def, @_since=@_since, @_to=@_to, @_publisherId=@_publisherId, @_node_id=@_node_id, @_item_id=@_item_id;

end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubMamQueryItemsCount')
	DROP PROCEDURE TigPubSubMamQueryItemsCount
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubMamQueryItemsCount
	@_nodes_ids nvarchar(max),
	@_since datetime,
	@_to datetime,
	@_publisher nvarchar(2049),
	@_order int
AS
begin
	declare
	    @_publisherId bigint,
	    @_ts nvarchar(20),
		@params_def nvarchar(max),
		@query_sql nvarchar(max);

    if @_publisher is not null
        begin
        select @_publisherId=jid_id
        from tig_pubsub_jids
        where jid_sha1 = HASHBYTES('SHA1', LOWER(@_publisher));
        end

    set @_ts = N'creation_date';
    if @_order = 1
        begin
        set @_ts = N'update_date';
        end

	set @params_def = N'@_since datetime, @_to datetime, @_publisherId bigint';

	set @query_sql = N';select count(1)
        from tig_pubsub_items pi
        where
            pi.node_id in (' + @_nodes_ids + ')
            and (@_since is null or pi.' + @_ts + ' >= @_since)
            and (@_to is null or pi.' + @_ts + ' <= @_to)
            and (@_publisherId is null or pi.publisher_id = @_publisherId)';

	execute sp_executesql @query_sql, @params_def, @_since=@_since, @_to=@_to, @_publisherId=@_publisherId;

end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubCreateNode')
	DROP PROCEDURE TigPubSubCreateNode
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubCreateNode
	@_service_jid nvarchar(2049),
	@_node_name nvarchar(1024),
	@_node_type int,
	@_node_creator nvarchar(2049),
	@_node_conf nvarchar(max),
	@_collection_id bigint,
	@_ts datetime
AS
begin
	declare @_service_id bigint;
	declare @_node_creator_id bigint;

	exec TigPubSubEnsureServiceJid @_service_jid=@_service_jid, @_service_id=@_service_id output;
	exec TigPubSubEnsureJid @_jid=@_node_creator, @_jid_id=@_node_creator_id output;

	BEGIN TRY
		insert into dbo.tig_pubsub_nodes (service_id, name, name_sha1, type, creator_id, creation_date, configuration, collection_id)
				select @_service_id, @_node_name, HASHBYTES('SHA1', @_node_name), @_node_type, @_node_creator_id, @_ts, @_node_conf, @_collection_id where not exists(
							select 1 from tig_pubsub_nodes where service_id=@_service_id AND name_sha1=HASHBYTES('SHA1', @_node_name));

		select @@IDENTITY as node_id;

  END TRY
  BEGIN CATCH
      IF ERROR_NUMBER() = 2627
				select node_id from tig_pubsub_nodes where service_id=@_service_id AND name_sha1=HASHBYTES('SHA1', @_node_name)
			ELSE
					declare @ErrorMessage nvarchar(max), @ErrorSeverity int, @ErrorState int;
					select @ErrorMessage = ERROR_MESSAGE() + ' Line ' + cast(ERROR_LINE() as nvarchar(5)), @ErrorSeverity = ERROR_SEVERITY(), @ErrorState = ERROR_STATE();
					raiserror (@ErrorMessage, @ErrorSeverity, @ErrorState);
  END CATCH

end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubWriteItem')
	DROP PROCEDURE TigPubSubWriteItem
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubWriteItem
	@_node_id bigint,
	@_item_id nvarchar(1024),
	@_publisher nvarchar(2049),
	@_item_data ntext,
	@_ts datetime
AS
begin
    SET NOCOUNT ON;
	declare @_publisher_id bigint;

	exec TigPubSubEnsureJid @_jid=@_publisher, @_jid_id=@_publisher_id output;
	-- Update the row if it exists.
    UPDATE tig_pubsub_items
		SET publisher_id = @_publisher_id, data = @_item_data, update_date = @_ts
		WHERE tig_pubsub_items.node_id = @_node_id
			and tig_pubsub_items.id_index = CAST(@_item_id as nvarchar(255))
			and tig_pubsub_items.id = @_item_id;
	-- Insert the row if the UPDATE statement failed.
	IF (@@ROWCOUNT = 0 )
	BEGIN
		BEGIN TRY
				insert into tig_pubsub_items (node_id, id, id_sha1, creation_date, update_date, publisher_id, data)
				select @_node_id, @_item_id, HASHBYTES('SHA1',@_item_id), @_ts, @_ts, @_publisher_id, @_item_data where not exists(
					select 1 from tig_pubsub_items where node_id = @_node_id AND id_sha1 = HASHBYTES('SHA1',@_item_id));
		END TRY
		BEGIN CATCH
				IF ERROR_NUMBER() <> 2627
						declare @ErrorMessage nvarchar(max), @ErrorSeverity int, @ErrorState int;
						select @ErrorMessage = ERROR_MESSAGE() + ' Line ' + cast(ERROR_LINE() as nvarchar(5)), @ErrorSeverity = ERROR_SEVERITY(), @ErrorState = ERROR_STATE();
						raiserror (@ErrorMessage, @ErrorSeverity, @ErrorState);
		END CATCH
	END
end
-- QUERY END:
GO