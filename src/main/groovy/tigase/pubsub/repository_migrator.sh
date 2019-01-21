#!/bin/bash
#
# Tigase PubSub - Publish Subscribe component for Tigase
# Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. Look for COPYING file in the top folder.
# If not, see http://www.gnu.org/licenses/.
#

LIBS="/home/smoku/workspace/tigase-server/target /home/smoku/workspace/tigase-server/libs /home/smoku/workspace/tigase-pubsub/target /usr/share/tigase-server/lib"
for DIR in $LIBS; do CLASSPATH="`ls -d $DIR/*.jar 2>/dev/null | tr '\n' :`$CLASSPATH"; done
export CLASSPATH
#echo $CLASSPATH
exec ${0/%.sh/.groovy}
