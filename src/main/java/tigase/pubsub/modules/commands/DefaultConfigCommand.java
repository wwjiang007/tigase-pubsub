/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.modules.commands;

import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.db.UserRepository;
import tigase.eventbus.EventBus;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.modules.NodeConfigModule;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.logging.Logger;

@Bean(name = "default-config-adhoc", parent = PubSubComponent.class, active = true)
public class DefaultConfigCommand implements AdHocCommand {

	public static class DefaultNodeConfigurationChangedEvent {

	}

	@Inject
	private PubSubConfig config;

	@Inject
	private EventBus eventbus;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	@Inject
	private UserRepository userRepository;

	public DefaultConfigCommand() {
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");
			if (request.getAction() != null && "cancel".equals(request.getAction())) {

				response.cancelSession();
			} else if (data == null) {
				LeafNodeConfig defaultNodeConfig = new LeafNodeConfig("default");
				defaultNodeConfig.read(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
				response.getElements().add(defaultNodeConfig.getFormElement());
				response.startSession();
			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					LeafNodeConfig nodeConfig = new LeafNodeConfig("default");
					nodeConfig.read(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);

					NodeConfigModule.parseConf(nodeConfig, request.getCommand(), config);

					nodeConfig.write(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);

					eventbus.fire(new DefaultNodeConfigurationChangedEvent());

					Form f = new Form("result", "Info", "Default config saved.");

					response.getElements().add(f.getElement());
					response.completeSession();
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
		return "Default config";
	}

	@Override
	public String getNode() {
		return "default-config";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

}
