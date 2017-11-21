/*
 * AbstractNodeConfig.java
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

package tigase.pubsub;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Field.FieldType;
import tigase.form.Form;
import tigase.xml.Element;

/**
 * Class description
 *
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 * @version 5.0.0, 2010.03.27 at 05:11:05 GMT
 */
public abstract class AbstractNodeConfig {

	public static final String PUBSUB = "pubsub#";

	public static final String TIGASE = "tigase#";

	/**
	 * List with do-not-write elements
	 */
	protected final Form form = new Form("form", null, null);
	private final String nodeName;

	public AbstractNodeConfig(final String nodeName) {
		this.nodeName = nodeName;
		init();
	}

	public AbstractNodeConfig(final String nodeName, final AbstractNodeConfig config) {
		this.nodeName = nodeName;
		init();
		copyFrom(config);
	}

	public void add(Field f) {
		form.addField(f);
	}

	@Override
	public AbstractNodeConfig clone() throws CloneNotSupportedException {
		AbstractNodeConfig clone = getInstance(nodeName);

		clone.copyFrom(this);

		return clone;
	}

	public void copyFrom(AbstractNodeConfig c) {
		form.copyValuesFrom(c.form);
	}

	public void copyFromForm(Form f) {
		form.copyValuesFrom(f);
	}

	public String getBodyXslt() {
		return form.getAsString("pubsub#body_xslt");
	}

	public String getBodyXsltEmbedded() {
		String[] r = form.getAsStrings("pubsub#embedded_body_xslt");

		if (r == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder();

		for (String string : r) {
			sb.append(string);
		}

		return sb.toString();
	}

	public void setBodyXsltEmbedded(String xslt) {
		setValue("pubsub#embedded_body_xslt", xslt);
	}

	public String[] getChildren() {
		return form.getAsStrings("pubsub#children");
	}

	public String getCollection() {
		String d = form.getAsString("pubsub#collection");

		return (d == null) ? "" : d;
	}

	public void setCollection(String collectionNew) {
		setValue("pubsub#collection", collectionNew);
	}

	public CollectionItemsOrdering getCollectionItemsOrdering() {
		String tmp = form.getAsString(TIGASE + "collection_items_odering");

		if (tmp == null) {
			return null;
		} else {
			try {
				return CollectionItemsOrdering.valueOf(tmp);
			} catch (Throwable ex) {
				return null;
			}
		}
	}

	public String[] getDomains() {
		String[] v = form.getAsStrings(PUBSUB + "domains");

		return (v == null) ? new String[]{} : v;
	}

	public void setDomains(String... domains) {
		setValues(PUBSUB + "domains", domains);
	}

	public Form getForm() {
		return form;
	}

	public Element getFormElement() {
		return form.getElement();
	}

	public AccessModel getNodeAccessModel() {
		String tmp = form.getAsString("pubsub#access_model");

		if (tmp == null) {
			return null;
		} else {
			return AccessModel.valueOf(tmp);
		}
	}

	public String getNodeName() {
		return nodeName;
	}

	public NodeType getNodeType() {
		String tmp = form.getAsString("pubsub#node_type");

		if (tmp == null) {
			return null;
		} else {
			return NodeType.valueOf(tmp);
		}
	}

	public void setNodeType(NodeType nodeType) {
		form.get("pubsub#node_type").setValues(new String[]{nodeType.name()});
	}

	public PublisherModel getPublisherModel() {
		String tmp = form.getAsString("pubsub#publish_model");

		if (tmp == null) {
			return null;
		} else {
			try {
				return PublisherModel.valueOf(tmp);
			} catch (Throwable ex) {
				return null;
			}
		}
	}

	public String[] getRosterGroupsAllowed() {
		return form.getAsStrings("pubsub#roster_groups_allowed");
	}

	public SendLastPublishedItem getSendLastPublishedItem() {
		String s = form.getAsString(PUBSUB + "send_last_published_item");
		try {
			return s == null ? SendLastPublishedItem.never : SendLastPublishedItem.valueOf(s);
		} catch (Exception e) {
			return SendLastPublishedItem.never;
		}
	}

	public String getTitle() {
		return form.getAsString("pubsub#title");
	}

	public boolean isAllowToViewSubscribers() {
		return form.getAsBoolean(TIGASE + "allow_view_subscribers");
	}

	public boolean isCollectionSet() {
		return form.get(PUBSUB + "collection") != null;
	}

	public boolean isDeliver_payloads() {
		return form.getAsBoolean("pubsub#deliver_payloads");
	}

	public boolean isDeliverPresenceBased() {
		return form.getAsBoolean("pubsub#presence_based_delivery");
	}

	public boolean isNotify_config() {
		return form.getAsBoolean("pubsub#notify_config");
	}

	public boolean isPresenceExpired() {
		Boolean x = form.getAsBoolean(TIGASE + "presence_expired");

		return (x == null) ? false : x.booleanValue();
	}

	public boolean isTigaseNotifyChangeSubscriptionAffiliationState() {
		return form.getAsBoolean(PUBSUB + "notify_sub_aff_state");
	}

	public void read(final UserRepository repository, final PubSubConfig config, final String subnode)
			throws UserNotFoundException, TigaseDBException {
		if (repository == null) {
			return;
		}

		String[] keys = repository.getKeys(config.getServiceBareJID(), subnode);

		if (keys != null) {
			for (String key : keys) {
				String[] values = repository.getDataList(config.getServiceBareJID(), subnode, key);

				setValues(key, values);
			}
		}
	}

	public void reset() {
		form.clear();
		init();
	}

	public void setValue(String var, boolean data) {
		setValue(var, new Boolean(data));
	}

	public void setValue(String var, Object data) {
		Field f = form.get(var);

		if (f == null) {
			return;
		} else {
			if (data == null) {
				f.setValues(new String[]{});
			} else {
				if (data instanceof String) {
					String str = (String) data;

					if ((f.getType() == FieldType.bool) && !"0".equals(str) && !"1".equals(str) &&
							!"false".equals(str) && !"true".equals(str)) {
						throw new RuntimeException(
								"Boolean fields allows only '1', '0', 'true' " + "and 'false' values");
					}

					f.setValues(new String[]{str});
				} else {
					if ((data instanceof Boolean) && (f.getType() == FieldType.bool)) {
						boolean b = ((Boolean) data).booleanValue();

						f.setValues(new String[]{b ? "1" : "0"});
					} else {
						if ((data instanceof String[]) &&
								((f.getType() == FieldType.list_multi) || (f.getType() == FieldType.text_multi))) {
							String[] d = (String[]) data;

							f.setValues(d);
						} else {
							throw new RuntimeException(
									"Cannot match type " + data.getClass().getCanonicalName() + " to field type " +
											f.getType().name());
						}
					}
				}
			}
		}
	}

	public void setValues(String var, String[] data) {
		if ((data == null) || (data.length > 1)) {
			setValue(var, data);
		} else {
			if (data.length == 0) {
				setValue(var, null);
			} else {
				setValue(var, data[0]);
			}
		}
	}

	@Override
	public String toString() {
		return "AbstractNodeConfig{" + "form=" + form + ", nodeName=" + nodeName + '}';
	}

	public void write(final UserRepository repo, final PubSubConfig config, final String subnode)
			throws UserNotFoundException, TigaseDBException {
		if (repo == null) {
			return;
		}

		repo.setData(config.getServiceBareJID(), subnode, "configuration", form.getElement().toString());
	}

	protected String[] asStrinTable(Enum<?>[] values) {
		String[] result = new String[values.length];
		int i = 0;

		for (Enum<?> v : values) {
			result[i++] = v.name();
		}

		return result;
	}

	protected abstract AbstractNodeConfig getInstance(String nodeName);

	protected void init() {
		form.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#node_config"));
		form.addField(Field.fieldListSingle(PUBSUB + "node_type", null, null, null,
											new String[]{NodeType.leaf.name(), NodeType.collection.name()}));
		form.addField(Field.fieldTextSingle(PUBSUB + "title", "", "A friendly name for the node"));
		form.addField(Field.fieldBoolean(PUBSUB + "deliver_payloads", true,
										 "Whether to deliver payloads with event notifications"));

		form.addField(Field.fieldBoolean(PUBSUB + "notify_config", false,
										 "Notify subscribers when the node configuration changes"));
		// form.addField(Field.fieldBoolean(PUBSUB + "notify_delete",
		// false,
		// "Notify subscribers when the node is deleted"));

		// form.addField(Field.fieldBoolean(PUBSUB + "notify_retract", false,
		// "Notify subscribers when items are removed from the node"));
		form.addField(Field.fieldBoolean(PUBSUB + "persist_items", true, "Persist items to storage"));
		form.addField(Field.fieldTextSingle(PUBSUB + "max_items", "10", "Max # of items to persist"));
		// form.addField(Field.fieldBoolean(PUBSUB + "subscribe", true,
		// "Whether to allow subscriptions"));
		form.addField(
				Field.fieldTextSingle(PUBSUB + "collection", "", "The collection with which a node is affiliated"));
		form.addField(
				Field.fieldListSingle(PUBSUB + "access_model", AccessModel.open.name(), "Specify the subscriber model",
									  null, asStrinTable(AccessModel.values())));
		form.addField(Field.fieldListSingle(PUBSUB + "publish_model", PublisherModel.publishers.name(),
											"Specify the publisher model", null,
											asStrinTable(PublisherModel.values())));
		form.addField(Field.fieldListSingle(PUBSUB + "send_last_published_item", SendLastPublishedItem.on_sub.name(),
											"When to send the last published item", null,
											asStrinTable(SendLastPublishedItem.values())));
		form.addField(Field.fieldTextMulti(PUBSUB + "domains", new String[]{},
										   "The domains allowed to access this node (blank for any)"));
		form.addField(Field.fieldBoolean(PUBSUB + "presence_based_delivery", false,
										 "Whether to deliver notifications to available users only"));
		form.addField(Field.fieldBoolean(TIGASE + "presence_expired", false,
										 "Whether to subscription expired when subscriber going offline."));
		form.addField(Field.fieldTextMulti(PUBSUB + "embedded_body_xslt", new String[]{},
										   "The XSL transformation which can be applied to payloads in order to generate an " +
												   "appropriate message body element."));
		form.addField(Field.fieldTextSingle(PUBSUB + "body_xslt", "",
											"The URL of an XSL transformation which can be applied to payloads in order to " +
													"generate an appropriate message body element."));
		form.addField(Field.fieldTextMulti(PUBSUB + "roster_groups_allowed", new String[]{},
										   "Roster groups allowed to subscribe"));
		form.addField(Field.fieldBoolean(PUBSUB + "notify_sub_aff_state", true,
										 "Notify subscribers when owner change their subscription or affiliation state"));
		form.addField(Field.fieldBoolean(TIGASE + "allow_view_subscribers", false,
										 "Allows get list of subscribers for each sybscriber"));
		form.addField(
				Field.fieldListSingle(TIGASE + "collection_items_odering", CollectionItemsOrdering.byUpdateDate.name(),
									  "Whether to sort collection items by creation date or update time", null,
									  asStrinTable(CollectionItemsOrdering.values())));

	}
}
