/**
 * Tigase PubSub - Publish Subscribe component for Tigase
 * Copyright (C) 2008 Tigase, Inc. (office@tigase.com)
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
package tigase.pubsub.modules.commands;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

public class ReadAllNodesCommand implements AdHocCommand {

	private final PubSubConfig config;
	private final PubSubDAO dao;
	private final IPubSubRepository repository;

	public ReadAllNodesCommand(PubSubConfig config, PubSubDAO directPubSubRepository, IPubSubRepository pubsubRepository) {
		this.dao = directPubSubRepository;
		this.repository = pubsubRepository;
		this.config = config;
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");
			if (request.getAction() != null && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else if (data == null) {
				Form form = new Form("form", "Reading all nodes", "To read all nodes from DB press finish");

				response.getElements().add(form.getElement());
				response.startSession();

			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					startReading(request.getIq().getStanzaTo().getBareJID());
					Form f = new Form("result", "Info", "Nodes tree has been readed");
					response.getElements().add(f.getElement());
				}
				response.completeSession();
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Read ALL nodes";
	}

	@Override
	public String getNode() {
		return "read-all-nodes";
	}

	private void startReading(BareJID serviceJid) throws RepositoryException {
		final String[] allNodesId = dao.getAllNodesList(serviceJid);
		for (String n : allNodesId) {
			repository.getNodeConfig(serviceJid, n);
		}
	}

}
