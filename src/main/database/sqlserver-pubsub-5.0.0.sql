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
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'component_name' AND object_id = object_id('dbo.tig_pubsub_service_jids'))
    ALTER TABLE [tig_pubsub_service_jids] ADD [component_name] [nvarchar](190);
-- QUERY END:
GO

-- QUERY START:
IF (SELECT count(1) FROM [tig_pubsub_service_jids] WHERE component_name IS NULL) > 0
    update tig_pubsub_service_jids set component_name = (select TOP 1 name from (
        select name
        from (
            select distinct LEFT(service_jid, CHARINDEX('.', service_jid)) as name
            from tig_pubsub_service_jids where service_jid not like '%@%' and CHARINDEX('.', service_jid) > 0
        ) x
        union
        select 'pubsub' as name
    ) y) where component_name is null;
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.columns WHERE name = 'component_name' AND object_id = object_id('dbo.tig_pubsub_service_jids') AND is_nullable = 1)
    ALTER TABLE [tig_pubsub_service_jids] ALTER COLUMN [component_name] [nvarchar](190) NOT NULL ;
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_pubsub_service_jids') AND NAME ='IX_tig_pubsub_service_jids_component_name')
    CREATE INDEX IX_tig_pubsub_service_jids_component_name ON [tig_pubsub_service_jids](component_name);
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
	@_component_name nvarchar(190),
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
			insert into tig_pubsub_service_jids (service_jid,service_jid_sha1,component_name)
				select @_service_jid, @_service_jid_sha1, @_component_name where not exists(
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
	@_component_name nvarchar(190)
AS
begin
	declare @_service_id bigint;
	declare @_node_creator_id bigint;

	exec TigPubSubEnsureServiceJid @_service_jid=@_service_jid, @_component_name=@_component_name, @_service_id=@_service_id output;
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
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubRemoveService')
	DROP PROCEDURE TigPubSubRemoveService
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.TigPubSubRemoveService
	@_service_jid nvarchar(2049),
	@_component_name nvarchar(190)
AS
begin
	declare @_service_id bigint;

	select @_service_id=service_id from tig_pubsub_service_jids where service_jid_sha1 = HASHBYTES('SHA1', @_service_jid) and component_name = @_component_name;

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
