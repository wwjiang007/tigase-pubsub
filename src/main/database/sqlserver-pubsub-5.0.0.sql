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
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'domain' AND object_id = object_id('dbo.tig_pubsub_service_jids'))
BEGIN
    ALTER TABLE [tig_pubsub_service_jids] ADD [domain] [nvarchar](1024);
    ALTER TABLE [tig_pubsub_service_jids] ADD [domain_sha1] [varbinary](40);
    ALTER TABLE [tig_pubsub_service_jids] ADD [is_public] [int] not null default 0;
END
-- QUERY END:
GO

-- QUERY START:
IF (SELECT count(1) FROM [tig_pubsub_service_jids] WHERE domain IS NULL) > 0
    update tig_pubsub_service_jids set domain = CASE CHARINDEX('@', service_jid)
        WHEN 0 THEN service_jid
        ELSE SUBSTRING(service_jid, CHARINDEX('@', service_jid) + 1, LEN(service_jid))
        end;
    update tig_pubsub_service_jids set domain_sha1 = HASHBYTES('SHA1', LOWER(domain));
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.columns WHERE name = 'domain' AND object_id = object_id('dbo.tig_pubsub_service_jids') AND is_nullable = 1)
    ALTER TABLE [tig_pubsub_service_jids] ALTER COLUMN [domain] [nvarchar](1024) NOT NULL;
    ALTER TABLE [tig_pubsub_service_jids] ALTER COLUMN [domain_sha1] [varbinary](40) NOT NULL;
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_pubsub_service_jids') AND NAME ='IX_tig_pubsub_service_jids_domain_is_public')
    CREATE INDEX IX_tig_pubsub_service_jids_domain_is_public ON [tig_pubsub_service_jids](domain, is_public);
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
	@_domain nvarchar(1024),
	@_autocreateService int,
	@_service_id bigint OUTPUT
AS
begin
	declare @_service_jid_sha1 varbinary(20);

	set @_service_jid_sha1 = HASHBYTES('SHA1', LOWER(@_service_jid));

	begin transaction;
    select @_service_id=service_id from tig_pubsub_service_jids
		where service_jid_sha1 = @_service_jid_sha1;

	if @_service_id is null and @_autocreateService > 0
		begin
			insert into tig_pubsub_service_jids (service_jid,service_jid_sha1,domain,domain_sha1)
				select @_service_jid, @_service_jid_sha1, @_domain, HASHBYTES('SHA1', LOWER(@_domain));
			set @_service_id = @@IDENTITY
		end

	commit transaction;
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
	@_ts datetime,
	@_domain nvarchar(1024),
	@_autocreateService int
AS
begin
	declare @_service_id bigint;
	declare @_node_creator_id bigint;

	exec TigPubSubEnsureJid @_jid=@_node_creator, @_jid_id=@_node_creator_id output;

    begin transaction;
	exec TigPubSubEnsureServiceJid @_service_jid=@_service_jid, @_domain=@_domain, @_autocreateService=@_autocreateService, @_service_id=@_service_id output;

	insert into dbo.tig_pubsub_nodes (service_id, name, name_sha1, type, creator_id, creation_date, configuration, collection_id)
			values (@_service_id, @_node_name, HASHBYTES('SHA1', @_node_name), @_node_type, @_node_creator_id, @_ts, @_node_conf, @_collection_id);

	select @@IDENTITY as node_id;

    commit transaction;
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

	select @_service_id=service_id from tig_pubsub_service_jids where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid);

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
		select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = HASHBYTES('SHA1', @_service_jid) and j.jid = @_service_jid);
	delete from tig_pubsub_subscriptions where jid_id in (
		select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = HASHBYTES('SHA1', @_service_jid) and j.jid = @_service_jid);
end
-- QUERY END:
GO


-- QUERY START:
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'tig_pubsub_mam')
	CREATE  TABLE [dbo].[tig_pubsub_mam] (
		[node_id] [bigint] NOT NULL,
		[uuid] [uniqueidentifier] NOT NULL,
		[item_id] [nvarchar](1024),
		[item_id_sha1] [varbinary](40),
		[ts] [datetime] NOT NULL,
		[data] [nvarchar](MAX),
		PRIMARY KEY (node_id,uuid),
		CONSTRAINT [FK_tig_pubsub_mam_node_id] FOREIGN KEY ([node_id])
			REFERENCES [dbo].[tig_pubsub_nodes](node_id) ON DELETE CASCADE
	);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'uuid' AND object_id = object_id('dbo.tig_pubsub_items'))
    ALTER TABLE [tig_pubsub_items] ADD [uuid] [uniqueidentifier];
-- QUERY END:
GO


-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_pubsub_mam') AND NAME ='IX_tig_pubsub_mam_node_id_item_id_sha1')
	CREATE INDEX IX_tig_pubsub_mam_node_id_item_id_sha1 ON [dbo].[tig_pubsub_mam](node_id, item_id_sha1);
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteItem')
	DROP PROCEDURE TigPubSubDeleteItem
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubDeleteItem
	@_node_id bigint,
	@_item_id nvarchar(1024)
AS
begin
	delete from tig_pubsub_items where node_id = @_node_id
		and id_index = CAST(@_item_id as NVARCHAR(255)) AND id = @_item_id ;
	update tig_pubsub_mam set data = null where node_id = @_node_id and item_id_sha1 = HASHBYTES('SHA1', @_item_id) AND item_id = @_item_id;
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
	@_ts datetime,
	@_uuid nvarchar(36)
AS
begin
    SET NOCOUNT ON;
	declare @_publisher_id bigint;

	exec TigPubSubEnsureJid @_jid=@_publisher, @_jid_id=@_publisher_id output;
	-- Update the row if it exists.
    UPDATE tig_pubsub_items
		SET publisher_id = @_publisher_id, data = @_item_data, update_date = @_ts, uuid = CONVERT(uniqueidentifier, @_uuid)
		WHERE tig_pubsub_items.node_id = @_node_id
			and tig_pubsub_items.id_index = CAST(@_item_id as nvarchar(255))
			and tig_pubsub_items.id = @_item_id;
	-- Insert the row if the UPDATE statement failed.
	IF (@@ROWCOUNT = 0 )
	BEGIN
		BEGIN TRY
				insert into tig_pubsub_items (node_id, id, id_sha1, creation_date, update_date, publisher_id, data, uuid)
				select @_node_id, @_item_id, HASHBYTES('SHA1',@_item_id), @_ts, @_ts, @_publisher_id, @_item_data, CONVERT(uniqueidentifier, @_uuid) where not exists(
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

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubMamAddItem')
	DROP PROCEDURE TigPubSubMamAddItem
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubMamAddItem
	@_node_id bigint,
	@_uuid nvarchar(36),
	@_ts datetime,
	@_item_data ntext,
	@_item_id nvarchar(1024)
AS
begin
    SET NOCOUNT ON;
    insert into tig_pubsub_mam (node_id, uuid, ts, data, item_id)
	    values (@_node_id, CONVERT(uniqueidentifier,@_uuid), @_ts, @_item_data, @_item_id);
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
	@_node_id bigint,
	@_since datetime,
	@_to datetime,
	@_limit int,
	@_offset int
AS
begin
    with results_cte as(
	    select pm.uuid, pm.ts, pm.data, row_number() over (order by pm.ts) as row_num
        from tig_pubsub_mam pm
        where
            pm.node_id = @_node_id
            and (@_since is null or pm.ts >= @_since)
            and (@_to is null or pm.ts <= @_to)
    ) select convert(nvarchar(36),uuid), ts, data, row_num from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num
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
	@_node_id bigint,
	@_since datetime,
	@_to datetime,
	@_uuid nvarchar(36)
AS
begin
    with results_cte as(
	    select pm.uuid as uuid, row_number() over (order by pm.ts) as position
        from tig_pubsub_mam pm
        where
            pm.node_id = @_node_id
            and (@_since is null or pm.ts >= @_since)
            and (@_to is null or pm.ts <= @_to)
    ) select position from results_cte where uuid = convert(uniqueidentifier,@_uuid)
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
	@_node_id bigint,
	@_since datetime,
	@_to datetime
AS
begin
    select count(1)
        from tig_pubsub_mam pm
        where
            pm.node_id = @_node_id
            and (@_since is null or pm.ts >= @_since)
            and (@_to is null or pm.ts <= @_to)
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetItem')
	DROP PROCEDURE TigPubSubGetItem
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetItem
	@_node_id bigint,
	@_item_id nvarchar(1024)
AS
begin
  select pi.data, pn.name, convert(nvarchar(36), pi.uuid)
    from dbo.tig_pubsub_items pi
	inner join tig_pubsub_nodes pn on pn.node_id = pi.node_id
	where pi.node_id = @_node_id AND pi.id_sha1 = HASHBYTES('SHA1', @_item_id) AND id = @_item_id;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubQueryItems')
	DROP PROCEDURE TigPubSubQueryItems
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubQueryItems
	@_nodes_ids nvarchar(max),
	@_since datetime,
	@_to datetime,
	@_order int,
	@_limit int,
	@_offset int
AS
begin
	declare
	    @_ts nvarchar(20),
		@params_def nvarchar(max),
		@query_sql nvarchar(max);

    set @_ts = N'creation_date';
    if @_order = 1
        begin
        set @_ts = N'update_date';
        end

	set @params_def = N'@_since datetime, @_to datetime,  @_offset int, @_limit int';

	set @query_sql = N';with results_cte as(
	    select pn.name, pi.node_id, pi.uuid, pi.data, row_number() over (order by pi.' + @_ts + ') as row_num
        from tig_pubsub_items pi
            inner join tig_pubsub_nodes pn on pi.node_id = pn.node_id
        where
            pi.node_id in (' + @_nodes_ids + ')
            and (@_since is null or pi.' + @_ts + ' >= @_since)
            and (@_to is null or pi.' + @_ts + ' <= @_to)
    ) select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num';

	execute sp_executesql @query_sql, @params_def, @_since=@_since, @_to=@_to, @_offset=@_offset, @_limit=@_limit;

end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubQueryItemPosition')
	DROP PROCEDURE TigPubSubQueryItemPosition
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubQueryItemPosition
	@_nodes_ids nvarchar(max),
	@_since datetime,
	@_to datetime,
	@_order int,
	@_node_id bigint,
	@_item_id nvarchar(1024)
AS
begin
	declare
	    @_ts nvarchar(20),
		@params_def nvarchar(max),
		@query_sql nvarchar(max);

    set @_ts = N'creation_date';
    if @_order = 1
        begin
        set @_ts = N'update_date';
        end

	set @params_def = N'@_since datetime, @_to datetime,  @_node_id bigint, @_item_id nvarchar(1024)';

	set @query_sql = N';with results_cte as(
	    select pi.node_id, pi.id, row_number() over (order by pi.' + @_ts + ') as position
        from tig_pubsub_items pi
        where
            pi.node_id in (' + @_nodes_ids + ')
            and (@_since is null or pi.' + @_ts + ' >= @_since)
            and (@_to is null or pi.' + @_ts + ' <= @_to)
    ) select position from results_cte where node_id = @_node_id and id = @_item_id';

	execute sp_executesql @query_sql, @params_def, @_since=@_since, @_to=@_to, @_node_id=@_node_id, @_item_id=@_item_id;

end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubQueryItemsCount')
	DROP PROCEDURE TigPubSubQueryItemsCount
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubQueryItemsCount
	@_nodes_ids nvarchar(max),
	@_since datetime,
	@_to datetime,
	@_order int
AS
begin
	declare
	    @_publisherId bigint,
	    @_ts nvarchar(20),
		@params_def nvarchar(max),
		@query_sql nvarchar(max);

    set @_ts = N'creation_date';
    if @_order = 1
        begin
        set @_ts = N'update_date';
        end

	set @params_def = N'@_since datetime, @_to datetime';

	set @query_sql = N';select count(1)
        from tig_pubsub_items pi
        where
            pi.node_id in (' + @_nodes_ids + ')
            and (@_since is null or pi.' + @_ts + ' >= @_since)
            and (@_to is null or pi.' + @_ts + ' <= @_to)';

	execute sp_executesql @query_sql, @params_def, @_since=@_since, @_to=@_to;

end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeItemsMeta')
	DROP PROCEDURE TigPubSubGetNodeItemsMeta
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetNodeItemsMeta
	@_node_id bigint
AS
begin
	select id, creation_date, update_date, convert(varchar(36), uuid)  from tig_pubsub_items where node_id = @_node_id order by creation_date;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetServices')
	DROP PROCEDURE TigPubSubGetServices
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubGetServices
	@_domain nvarchar(1024),
	@_is_public int
AS
begin
    select service_jid, is_public from tig_pubsub_service_jids where domain_sha1 = HASHBYTES('SHA1', @_domain) and (@_is_public is null or is_public = @_is_public) order by service_jid;
end
-- QUERY END:
GO


-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubCreateService')
	DROP PROCEDURE TigPubSubCreateService
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubCreateService
	@_service_jid nvarchar(2049),
	@_domain nvarchar(1024),
	@_is_public int
AS
begin
	insert into tig_pubsub_service_jids (service_jid,service_jid_sha1,domain,domain_sha1,is_public)
		values (@_service_jid, HASHBYTES('SHA1', LOWER(@_service_jid)), @_domain, HASHBYTES('SHA1', LOWER(@_domain)), @_is_public);
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubRemoveNode')
DROP PROCEDURE TigPubSubRemoveNode
-- QUERY END:
    GO

-- QUERY START:
create procedure dbo.TigPubSubRemoveNode
    @_node_id bigint
AS
begin
    delete from dbo.tig_pubsub_mam where node_id = @_node_id;
    delete from dbo.tig_pubsub_items where node_id = @_node_id;
    delete from dbo.tig_pubsub_subscriptions where node_id = @_node_id;
    delete from dbo.tig_pubsub_affiliations where node_id = @_node_id;
    delete from dbo.tig_pubsub_nodes where node_id = @_node_id;
end
-- QUERY END:
GO




