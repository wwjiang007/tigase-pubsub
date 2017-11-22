/*
 * ViewNodeLoadCommand.java
 *
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */

package tigase.pubsub.cluster;

import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ViewNodeLoadCommand
		implements AdHocCommand {

	public static final Logger log = Logger.getLogger(ViewNodeLoadCommand.class.getName());

	private final PubSubConfig config;

	private final ClusterNodeMap nodeMap;

	public ViewNodeLoadCommand(PubSubConfig config, ClusterNodeMap nodeMap) {
		this.config = config;
		this.nodeMap = nodeMap;
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			if (!config.isAdmin(request.getSender())) {
				throw new AdHocCommandException(Authorization.FORBIDDEN);
			}

			Form form = new Form("result", "Cluster nodes load", "Statistics of cluster nodes");

			for (Entry<String, Integer> entry : this.nodeMap.getClusterNodesLoad().entrySet()) {
				Field field = Field.fieldTextSingle("tigase#node-" + entry.getKey(), entry.getValue().toString(),
													entry.getKey());
				form.addField(field);

			}

			response.getElements().add(form.getElement());
			response.completeSession();

		} catch (AdHocCommandException e) {
			throw e;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing vie node load packet", e);
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "View cluster load";
	}

	@Override
	public String getNode() {
		return "cluster-load";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

}
