/*
 * PubSubComponent.java
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

import tigase.component.AbstractKernelBasedComponent;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.impl.AdHocCommandModule;
import tigase.component.modules.impl.JabberVersionModule;
import tigase.component.modules.impl.XmppPingModule;
import tigase.conf.Configurable;
import tigase.db.UserRepository;
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.selector.ClusterModeRequired;
import tigase.kernel.beans.selector.ConfigType;
import tigase.kernel.beans.selector.ConfigTypeEnum;
import tigase.kernel.core.Kernel;
import tigase.pubsub.modules.XsltTool;
import tigase.pubsub.modules.commands.DefaultConfigCommand;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.stats.StatisticHolder;
import tigase.stats.StatisticsList;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;
import tigase.xmpp.mam.modules.GetFormModule;

import javax.script.Bindings;
import java.util.Queue;
import java.util.logging.Level;

/**
 * Class description
 *
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 * @version 5.1.0, 2010.11.02 at 01:05:02 MDT
 */
@Bean(name = "pubsub", parent = Kernel.class, active = true)
@ConfigType(ConfigTypeEnum.DefaultMode)
@ClusterModeRequired(active = false)
public class PubSubComponent
		extends AbstractKernelBasedComponent
		implements Configurable, DisableDisco {

	public static final String DEFAULT_LEAF_NODE_CONFIG_KEY = "default-node-config";
	public static final String EVENT_XMLNS = "tigase:events:pubsub";
	private static final String COMPONENT = "component";

	// ~--- fields
	// ---------------------------------------------------------------
	@Inject(bean = "defaultNodeConfig")
	protected LeafNodeConfig defaultNodeConfig;
	protected Integer maxRepositoryCacheSize;
	@Inject
	private IPubSubRepository pubsubRepository;

	// ~--- methods
	// --------------------------------------------------------------
	private XsltTool xslTransformer;

	public PubSubComponent() {
	}

	@Override
	public void everyHour() {
		super.everyHour();
		if (pubsubRepository instanceof StatisticHolder) {
			((StatisticHolder) pubsubRepository).everyHour();
		}
	}

	@Override
	public void everyMinute() {
		super.everyMinute();
		if (pubsubRepository instanceof StatisticHolder) {
			((StatisticHolder) pubsubRepository).everyMinute();
		}
	}

	@Override
	public void everySecond() {
		super.everySecond();
		if (pubsubRepository instanceof StatisticHolder) {
			((StatisticHolder) pubsubRepository).everySecond();
		}
	}

	@Override
	public String getComponentVersion() {
		String version = this.getClass().getPackage().getImplementationVersion();
		return version == null ? "0.0.0" : version;
	}

	@Override
	public String getDiscoCategory() {
		return "pubsub";
	}

	@Override
	public String getDiscoCategoryType() {
		return "service";
	}

	@Override
	public String getDiscoDescription() {
		return "PubSub";
	}

	@Override
	public void getStatistics(StatisticsList list) {
		super.getStatistics(list);
		IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);
		if (pubsubRepository instanceof StatisticHolder) {
			((StatisticHolder) pubsubRepository).getStatistics(getName(), list);
		}
	}

	@Override
	public int hashCodeForPacket(Packet packet) {
		int hash = packet.hashCode();

		return hash;
	}

	@Override
	public void initBindings(Bindings binds) {
		super.initBindings(binds);
		binds.put(COMPONENT, this);
	}

	@Override
	public boolean isDiscoNonAdmin() {
		return true;
	}

	@Override
	public boolean isSubdomain() {
		return true;
	}

	@HandleEvent
	public void onChangeDefaultNodeConfig(DefaultConfigCommand.DefaultNodeConfigurationChangedEvent event) {
		try {
			PubSubConfig componentConfig = kernel.getInstance(PubSubConfig.class);
			UserRepository userRepository = kernel.getInstance(UserRepository.class);

			this.defaultNodeConfig.read(userRepository, componentConfig, DEFAULT_LEAF_NODE_CONFIG_KEY);
			log.info("Node " + getComponentId() + " read default node configuration.");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Reading default config error", e);
		}
	}

	@Override
	public int processingInThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	@Override
	public int processingOutThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	@Override
	public void processPacket(Packet packet) {
		if (!checkPubSubServiceJid(packet)) {
			return;
		}

		super.processPacket(packet);
	}

	@Override
	public boolean processScriptCommand(Packet pc, Queue<Packet> results) {
		if (!checkPubSubServiceJid(pc)) {
			return true;
		}
		return super.processScriptCommand(pc, results);
	}
	
	@Override
	public void start() {
		super.start();
		eventBus.registerAll(this);
	}

	@Override
	public void stop() {
		super.stop();
		eventBus.unregisterAll(this);
	}

	@HandleEvent
	public void onUserRemoved(UserRepository.UserRemovedEvent event) {
		try {
			IPubSubRepository pubsubRepository = kernel.getInstance(IPubSubRepository.class);
			pubsubRepository.onUserRemoved(event.jid);
		} catch (RepositoryException ex) {
			log.log(Level.WARNING, "could not remove PubSub data for removed user " + event.jid, ex);
		}
	}

	@Override
	protected void registerModules(final Kernel kernel) {
		kernel.registerBean(AdHocCommandModule.class).exec();
		kernel.registerBean(JabberVersionModule.class).exec();
		kernel.registerBean(XmppPingModule.class).exec();
		kernel.registerBean(GetFormModule.class).exec();
	}

	/**
	 * Method checks if packet is sent to pubsub@xxx and if so then it returns error as we no longer allow usage of
	 * pubsub@xxx address as pubsub service jid since we added support to use PEP and we have multiple domains support
	 * with separated nodes.
	 *
	 * @param packet
	 *
	 * @return true - if packet service jid is ok and should be processed
	 */
	protected boolean checkPubSubServiceJid(Packet packet) {
		// if stanza is addressed to getName()@domain then we need to return
		// SERVICE_UNAVAILABLE error
		if (packet.getStanzaTo() != null && getName().equals(packet.getStanzaTo().getLocalpart()) &&
				packet.getType() != StanzaType.result) {
			try {
				Packet result = Authorization.SERVICE_UNAVAILABLE.getResponseMessage(packet, null, true);
				addOutPacket(result);
			} catch (PacketErrorTypeException ex) {
				log.log(Level.FINE, "Packet already of type=error, while preparing error response", ex);
			}
			return false;
		}
		return true;
	}

}
