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


[[ "$1" = "" ]] && \
  echo "Give me a path to the location where you want to have the database created" && \
  exit 1

java -Dij.protocol=jdbc:derby: -Dij.database="$1;create=true" \
		-Dderby.system.home=`pwd` \
		-cp jars/derby.jar:jars/derbytools.jar:jars/tigase-server.jar:jars/tigase-pubsub.jar \
		org.apache.derby.tools.ij database/derby-pubsub-schema-4.0.0.sql &> derby-db-create.txt

echo -e "\n\n\nconfiguration:\n\nJDBC URI: jdbc:derby:$1\n\n"
