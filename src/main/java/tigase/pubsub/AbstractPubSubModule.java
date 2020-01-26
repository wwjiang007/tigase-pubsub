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
package tigase.pubsub;

import tigase.component.PacketWriter;
import tigase.component.modules.Module;
import tigase.kernel.beans.Inject;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.utils.PubSubLogic;
import tigase.server.Packet;
import tigase.stats.StatisticHolderImpl;
import tigase.xml.Element;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Base class for modules of PubSub component. Provides commonly used properties and methods.
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version 5.0.0, 2010.03.27 at 05:24:03 GMT
 */
public abstract class AbstractPubSubModule
		extends StatisticHolderImpl
		implements Module {

	protected final static Logger log = Logger.getLogger(AbstractPubSubModule.class.getName());
	@Inject
	protected IPubSubConfig config;
	@Inject
	protected PubSubLogic pubSubLogic;
	@Inject
	protected PacketWriter packetWriter;
	@Inject(nullAllowed = false)
	private IPubSubRepository repository;
	
	public static List<Element> makeArray(Element... elements) {
		LinkedList<Element> result = new LinkedList<Element>();

		for (Element element : elements) {
			result.add(element);
		}

		return result;
	}

	public static List<Packet> makeArray(Packet... packets) {
		LinkedList<Packet> result = new LinkedList<Packet>();

		for (Packet packet : packets) {
			result.add(packet);
		}

		return result;
	}

	public AbstractPubSubModule() {
		this.setStatisticsPrefix(getClass().getSimpleName());
	}

	protected IPubSubRepository getRepository() {
		return repository;
	}

}
