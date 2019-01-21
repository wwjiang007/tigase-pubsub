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
SET QUOTED_IDENTIFIER ON
-- QUERY END:
GO


-- renaming tables by adding suffix _1
-- QUERY START:
sp_rename tig_pubsub_items, tig_pubsub_items_1
-- QUERY END:
GO

-- QUERY START:
sp_rename tig_pubsub_subscriptions, tig_pubsub_subscriptions_1;
-- QUERY END:
GO

-- QUERY START:
sp_rename tig_pubsub_nodes, tig_pubsub_nodes_1;
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubCreateNode')
DROP PROCEDURE TigPubSubCreateNode
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubRemoveNode')
DROP PROCEDURE TigPubSubRemoveNode
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetItem')
DROP PROCEDURE TigPubSubGetItem
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubWriteItem')
DROP PROCEDURE TigPubSubWriteItem
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteItem')
DROP PROCEDURE TigPubSubDeleteItem
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeItemsIds')
DROP PROCEDURE TigPubSubGetNodeItemsIds
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetAllNodes')
DROP PROCEDURE TigPubSubGetAllNodes
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteAllNodes')
DROP PROCEDURE TigPubSubDeleteAllNodes
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubSetNodeConfiguration')
DROP PROCEDURE TigPubSubSetNodeConfiguration
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubSetNodeAffiliations')
DROP PROCEDURE TigPubSubSetNodeAffiliations
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeConfiguration')
DROP PROCEDURE TigPubSubGetNodeConfiguration
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeAffiliations')
DROP PROCEDURE TigPubSubGetNodeAffiliations
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubGetNodeSubscriptions')
DROP PROCEDURE TigPubSubGetNodeSubscriptions
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubSetNodeSubscriptions')
DROP PROCEDURE TigPubSubSetNodeSubscriptions
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'TigPubSubDeleteNodeSubscriptions')
DROP PROCEDURE TigPubSubDeleteNodeSubscriptions
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'FN' AND name = 'substrCount')
DROP FUNCTION substrCount
-- QUERY END:
GO


