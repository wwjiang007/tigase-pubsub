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
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubMamQueryItem')
DROP PROCEDURE TigPubSubMamQueryItem
-- QUERY END:
    GO

-- QUERY START:
create procedure dbo.TigPubSubMamQueryItem
    @_node_id bigint,
	@_uuid nvarchar(36)
AS
begin
    select pm.uuid, pm.ts, pm.data, row_number() over (order by pm.ts) as row_num
    from tig_pubsub_mam pm
    where
            pm.node_id = @_node_id
            and pm.uuid = convert(uniqueidentifier,@_uuid)
end
-- QUERY END:
GO