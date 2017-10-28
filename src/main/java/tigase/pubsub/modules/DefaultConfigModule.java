/*
 * DefaultConfigModule.java
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
package tigase.pubsub.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

@Bean(name = "defaultConfigModule", parent = PubSubComponent.class, active = true)
public class DefaultConfigModule extends AbstractPubSubModule {

	private static final Criteria CRIT_DEFAULT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("default"));

	@Inject(bean = "defaultNodeConfig")
	protected LeafNodeConfig defaultNodeConfig;

	public DefaultConfigModule() {
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-default" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_DEFAULT;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			Element pubsub = new Element("pubsub", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub#owner" });
			Element def = new Element("default");
			Element x = defaultNodeConfig.getFormElement();
			if (x == null) {
				throw new PubSubException(packet.getElement(), Authorization.FEATURE_NOT_IMPLEMENTED,
						new PubSubErrorCondition("unsupported", "config-node"));
			}
			def.addChild(x);
			pubsub.addChild(def);

			Packet result = packet.okResult(pubsub, 0);

			packetWriter.write(result);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

}
