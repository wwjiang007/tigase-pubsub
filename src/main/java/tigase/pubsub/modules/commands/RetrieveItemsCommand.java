/*
 * RetrieveItemsCommand.java
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
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.util.datetime.TimestampHelper;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "retrieveItemsCommand", parent = PubSubComponent.class, active = true)
public class RetrieveItemsCommand
		implements AdHocCommand {

	public static final Logger log = Logger.getLogger(RetrieveItemsCommand.class.getName());

	public static final String TIGASE_PUBSUB_INTERNAL_KEY = "tigase-pubsub#internal";

	public static final String TIGASE_PUBSUB_ITEMID_KEY = "tigase-pubsub#item-id";

	public static final String TIGASE_PUBSUB_NODENAME_KEY = "tigase-pubsub#node-name";

	public static final String TIGASE_PUBSUB_SERVICE_KEY = "tigase-pubsub#service-name";

	public static final String TIGASE_PUBSUB_TIMESTAMP_KEY = "tigase-pubsub#timestamp";
	private final TimestampHelper dtf = new TimestampHelper();
	@Inject
	private PubSubConfig config;
	@Inject
	private IPubSubRepository repository;

	@Inject
	private UserRepository userRepo;

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");

			if ((request.getAction() != null) && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else {
				if (data == null) {
					Form form = new Form("result", "Retrieve items", null);

					form.addField(Field.fieldTextSingle(TIGASE_PUBSUB_SERVICE_KEY, "", "Service name"));
					form.addField(Field.fieldTextSingle(TIGASE_PUBSUB_NODENAME_KEY, "", "Node name"));
					form.addField(Field.fieldTextSingle(TIGASE_PUBSUB_ITEMID_KEY, "", "Item ID"));
					form.addField(Field.fieldTextSingle(TIGASE_PUBSUB_TIMESTAMP_KEY, dtf.format(new Date()),
														"Items since"));
					form.addField(Field.fieldHidden(TIGASE_PUBSUB_INTERNAL_KEY, ""));

					response.getElements().add(form.getElement());
					response.startSession();
				} else {
					Form form = new Form(data);
					if ("submit".equals(form.getType())) {
						final Boolean rebuild = form.getAsBoolean("tigase-pubsub#delete-all");

						String serviceName = form.getAsString(TIGASE_PUBSUB_SERVICE_KEY);
						String nodeName = form.getAsString(TIGASE_PUBSUB_NODENAME_KEY);
						String nodeId = form.getAsString(TIGASE_PUBSUB_ITEMID_KEY);
						String timeStr = form.getAsString(TIGASE_PUBSUB_TIMESTAMP_KEY);
						final Date timestamp =
								timeStr == null || timeStr.trim().length() == 0 ? null : dtf.parseTimestamp(timeStr);
						String internalId = form.getAsString(TIGASE_PUBSUB_INTERNAL_KEY);

						final JID sender = request.getSender();

						// only admins commands and connadns from service-owner
						// can be executed
						if (!config.isAdmin(sender) && !sender.getBareJID().toString().equals(serviceName)) {
							throw new AdHocCommandException(Authorization.FORBIDDEN);
						}

						if (nodeName == null) {
							throw new AdHocCommandException(Authorization.BAD_REQUEST, "Empty node name.");
						} else if (timestamp == null && nodeId == null) {
							throw new AdHocCommandException(Authorization.BAD_REQUEST, "Invalid timestamp.");
						}

						// ==================

						Element f = new Element("x", new String[]{"xmlns"}, new String[]{"jabber:x:data"});
						IItems nodeItems;
						if (null != serviceName) {
							nodeItems = repository.getNodeItems(BareJID.bareJIDInstance(serviceName), nodeName);
						} else {
							f.addChild(new Element("title", "Items"));
							Element reported = new Element("reported");
							reported.addChild(new Element("field", new String[]{"var"}, new String[]{"id"}));
							f.addChild(reported);

							nodeItems = repository.getNodeItems(request.getIq().getTo().getBareJID(), nodeName);
						}
						if (null != nodeId) {
							final String[] itemsIds = nodeItems.getItemsIds();
							if (null != itemsIds && Arrays.asList(itemsIds).contains(nodeId)) {
								Element i = nodeItems.getItem(nodeId);
								Element field = new Element("field", new String[]{"var"}, new String[]{"item"});
								field.addChild(new Element("value", new Element[]{i}, null, null));

								f.addChild(field);
							}
						} else {
							String[] allItems = nodeItems.getItemsIdsSince(timestamp);
							for (String id : allItems) {
								Element i = new Element("item");
								Element fi = new Element("field", new String[]{"var"}, new String[]{"id"});
								fi.addChild(new Element("value", id));
								i.addChild(fi);
								f.addChild(i);
							}

							// ==================
						}

						if (null != internalId) {
							Field fieldHidden = Field.fieldHidden(TIGASE_PUBSUB_INTERNAL_KEY, internalId);
							f.addChild(fieldHidden.getElement());
						}
						response.getElements().add(f);
					}
					response.completeSession();
				}
			}
		} catch (AdHocCommandException e) {
			throw e;
		} catch (Exception e) {
			log.log(Level.FINE, "Error processing retrieve items packet", e);

			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Retrieve items";
	}

	@Override
	public String getNode() {
		return "retrieve-items";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

}
