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