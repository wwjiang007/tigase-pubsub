/*
 * DeleteAllNodesCommand.java
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

package tigase.pubsub.modules.commands;

import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.component.exceptions.RepositoryException;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;

/**
 * Class description
 *
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 * @version 5.0.0, 2010.03.27 at 05:11:57 GMT
 */
@Bean(name = "deleteAllNodesCommand", active = true)
public class DeleteAllNodesCommand
		implements AdHocCommand {

	@Inject
	private PubSubConfig config;

	@Inject
	private IPubSubDAO<?, ?, ?> dao;

	@Inject
	private UserRepository userRepo;

	/**
	 * Method description
	 *
	 * @param request
	 * @param response
	 *
	 * @throws AdHocCommandException
	 */
	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");

			if ((request.getAction() != null) && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else {
				if (data == null) {
					Form form = new Form("result", "Delete all nodes", "To DELETE ALL NODES please check checkbox.");

					form.addField(Field.fieldBoolean("tigase-pubsub#delete-all", Boolean.FALSE,
													 "YES! I'm sure! I want to delete all nodes"));
					response.getElements().add(form.getElement());
					response.startSession();
				} else {
					Form form = new Form(data);

					if ("submit".equals(form.getType())) {
						final Boolean rebuild = form.getAsBoolean("tigase-pubsub#delete-all");

						if ((rebuild != null) && (rebuild.booleanValue() == true)) {
							startRemoving(request.getIq().getStanzaTo().getBareJID());

							Form f = new Form(null, "Info", "Nodes has been deleted");

							response.getElements().add(f.getElement());
						} else {
							Form f = new Form(null, "Info", "Deleting cancelled.");

							response.getElements().add(f.getElement());
						}
					}

					response.completeSession();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	/**
	 * Method description
	 *
	 * @return
	 */
	@Override
	public String getName() {
		return "Deleting ALL nodes";
	}

	/**
	 * Method description
	 *
	 * @return
	 */
	@Override
	public String getNode() {
		return "delete-all-nodes";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

	private void startRemoving(BareJID serviceJid)
			throws RepositoryException, UserNotFoundException, TigaseDBException {
		dao.removeAllFromRootCollection(serviceJid);
		userRepo.removeSubnode(config.getServiceBareJID(), "nodes");
	}
}
