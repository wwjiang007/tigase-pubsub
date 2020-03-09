/*
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

import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.component.exceptions.RepositoryException;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.server.AbstractMessageReceiver;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "rebuildDatabaseCommand", parent = PubSubComponent.class, active = true)
public class RebuildDatabaseCommand
		implements AdHocCommand {

	private static final Logger log = Logger.getLogger(RebuildDatabaseCommand.class.getName());

	@Inject(bean = "service")
	private AbstractMessageReceiver component;
	
	@Inject
	private PubSubConfig config;

	@Inject
	private IPubSubDAO dao;

	public RebuildDatabaseCommand() {
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");
			if (request.getAction() != null && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else if (data == null) {
				Form form = new Form("result", "Rebuild nodes tree", "To rebuild tree of nodes please check checkbox.");

				form.addField(Field.fieldBoolean("tigase-pubsub#rebuild", Boolean.FALSE, "Rebuild nodes tree?"));

				response.getElements().add(form.getElement());
				response.startSession();

			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					final Boolean rebuild = form.getAsBoolean("tigase-pubsub#rebuild");

					if (rebuild != null && rebuild.booleanValue() == true) {
						startRebuild(request.getIq().getStanzaTo().getBareJID());
						Form f = new Form("result", "Info", "Nodes tree has been rebuild");
						response.getElements().add(f.getElement());
					} else {
						Form f = new Form("result", "Info", "Rebuild cancelled.");
						response.getElements().add(f.getElement());
					}
				}
				response.completeSession();
			}

		} catch (Exception e) {
			log.log(Level.FINE, "Error during rebuild database", e);
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Rebuild database";
	}

	@Override
	public String getNode() {
		return "rebuild-db";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

	private void startRebuild(BareJID serviceJid) throws RepositoryException {
		final String[] allNodesId = dao.getAllNodesList(serviceJid);
		final Set<String> rootCollection = new HashSet<String>();
		final Map<String, AbstractNodeConfig> nodeConfigs = new HashMap<String, AbstractNodeConfig>();
		for (String nodeName : allNodesId) {
			Object nodeId = dao.getNodeId(serviceJid, nodeName);
			String nodeConfigData = dao.getNodeConfig(serviceJid, nodeId);
			AbstractNodeConfig nodeConfig = dao.parseConfig(nodeName, nodeConfigData);
			nodeConfigs.put(nodeName, nodeConfig);
			if (nodeConfig instanceof CollectionNodeConfig) {
				CollectionNodeConfig collectionNodeConfig = (CollectionNodeConfig) nodeConfig;
				collectionNodeConfig.setChildren(null);
			}
		}

		// Collections and node children are in sync on database level so no point to check it here
//		for (Entry<String, AbstractNodeConfig> entry : nodeConfigs.entrySet()) {
//			final AbstractNodeConfig nodeConfig = entry.getValue();
//			final String nodeName = entry.getKey();
//			final String collectionNodeName = nodeConfig.getCollection();
//			if (collectionNodeName == null || collectionNodeName.equals("")) {
//				nodeConfig.setCollection("");
//				rootCollection.add(nodeName);
//			} else {
//				AbstractNodeConfig potentialParent = nodeConfigs.get(collectionNodeName);
//				if (potentialParent != null && potentialParent instanceof CollectionNodeConfig) {
//					CollectionNodeConfig collectionConfig = (CollectionNodeConfig) potentialParent;
//					collectionConfig.addChildren(nodeName);
//				} else {
//					nodeConfig.setCollection("");
//					rootCollection.add(nodeName);
//				}
//
//			}
//		}

		for (Entry<String, AbstractNodeConfig> entry : nodeConfigs.entrySet()) {
			final AbstractNodeConfig nodeConfig = entry.getValue();
			final String nodeName = entry.getKey();
			Object nodeId = dao.getNodeId(serviceJid, nodeName);
			Object collectionId = dao.getNodeId(serviceJid, nodeConfig.getCollection());
			dao.updateNodeConfig(serviceJid, nodeId, nodeConfig.getFormElement().toString(), collectionId);
		}

		dao.removeService(serviceJid, component.getName());

	}

}
