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

-- renaming tables by adding suffix _1
alter table tig_pubsub_items rename to tig_pubsub_items_1;
alter table tig_pubsub_subscriptions rename to tig_pubsub_subscriptions_1;
alter table tig_pubsub_nodes rename to tig_pubsub_nodes_1;

-- droping procedures and functions
drop procedure if exists TigPubSubCreateNode;
drop procedure if exists TigPubSubRemoveNode;
drop procedure if exists TigPubSubGetItem;
drop procedure if exists TigPubSubWriteItem;
drop procedure if exists TigPubSubDeleteItem;
drop procedure if exists TigPubSubGetNodeItemsIds;
drop procedure if exists TigPubSubGetAllNodes;
drop procedure if exists TigPubSubDeleteAllNodes;
drop procedure if exists TigPubSubSetNodeConfiguration;
drop procedure if exists TigPubSubSetNodeAffiliations;
drop procedure if exists TigPubSubGetNodeConfiguration;
drop procedure if exists TigPubSubGetNodeAffiliations;
drop procedure if exists TigPubSubGetNodeSubscriptions;
drop procedure if exists TigPubSubSetNodeSubscriptions;
drop procedure if exists TigPubSubDeleteNodeSubscriptions;
drop function if exists substrCount;