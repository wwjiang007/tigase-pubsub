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

import tigase.pubsub.IPubSubConfig;
import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.component.exceptions.RepositoryException;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.AbstractMessageReceiver;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "loadTestCommand", parent = PubSubComponent.class, active = true)
public class LoadTestCommand
		implements AdHocCommand {

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	@Inject(bean = "service")
	private AbstractMessageReceiver component;

	@Inject
	private IPubSubConfig config;

	@Inject
	private IPubSubRepository repository;

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			if (!config.isAdmin(request.getSender())) {
				throw new AdHocCommandException(Authorization.FORBIDDEN);
			}

			final Element data = request.getCommand().getChild("x", "jabber:x:data");

			if ((request.getAction() != null) && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else {
				if (data == null) {
					Form form = new Form("result", "Load Test", "To start load test fill the form");

					form.addField(Field.fieldTextSingle("nodeId", "", "Node"));
					form.addField(Field.fieldTextSingle("time", "60", "Time of the test [s]"));
					form.addField(Field.fieldTextSingle("frequency", "1", "Publishing frequency [push/s]"));
					form.addField(Field.fieldTextSingle("length", "20", "Published messages size"));
					form.addField(Field.fieldBoolean("nonBlocking", Boolean.FALSE, "Use non-blocking adding"));

					response.getElements().add(form.getElement());
					response.startSession();
				} else {
					Form form = new Form(data);

					if ("submit".equals(form.getType())) {
						final BareJID service = request.getIq().getStanzaTo().getBareJID();
						final long time = form.getAsLong("time");
						final long frequency = form.getAsLong("frequency");
						final int length = form.getAsInteger("length");
						final String nodeName = form.getAsString("nodeId");
						final Boolean nonBlocking = form.getAsBoolean("nonBlocking");

						AbstractNodeConfig cfg = repository.getNodeConfig(service, nodeName);

						if (cfg != null) {
							IAffiliations subscriptions = repository.getNodeAffiliations(service, nodeName);

							UsersAffiliation owner = null;
							UsersAffiliation publisher = null;

							for (UsersAffiliation a : subscriptions.getAffiliations()) {
								if (publisher == null && a.getAffiliation().isPublishItem()) {
									publisher = a;
								}
								if (owner == null && a.getAffiliation() == Affiliation.owner) {
									owner = a;
								}
								if (owner != null && publisher != null) {
									break;
								}
							}

							if (owner == null && publisher == null) {
								Form f = new Form(null, "Info", "Can't find publisher!");
								response.getElements().add(f.getElement());
							} else {
								startLoadTest(service, nodeName, owner != null ? owner.getJid() : publisher.getJid(),
											  time, frequency, length, nonBlocking == null ? true : !nonBlocking);

								Form f = new Form(null, "Info", "Load Test started");

								response.getElements().add(f.getElement());
							}
						} else {
							Form f = new Form(null, "Info",
											  "Load Test cancelled. Node " + nodeName + " doesn't exists.");
							response.getElements().add(f.getElement());
						}
					}

					response.completeSession();
				}
			}
		} catch (AdHocCommandException e) {
			throw e;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing load test command", e);
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Load Test";
	}

	@Override
	public String getNode() {
		return "load-test";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

	private void startLoadTest(BareJID serviceJid, String nodeName, BareJID publisher, Long time, Long frequency,
							   Integer length, boolean useBlockingMethod)
			throws RepositoryException, UserNotFoundException, TigaseDBException {

		final LoadTestGenerator r = new LoadTestGenerator(component, serviceJid, nodeName, publisher, time, frequency,
														  length, useBlockingMethod) {
			@Override
			protected void onTestFinish() {
				LoadTestCommand.this.log.log(Level.CONFIG, "Test finished. Published " + getCounter() + " items in " +
													  ((getTestEndTime() - getTestStartTime()) / 1000) + " seconds.");
			}
		};
		log.log(Level.CONFIG, "Staring load test.");
		(new Thread(r)).start();

	}
}
