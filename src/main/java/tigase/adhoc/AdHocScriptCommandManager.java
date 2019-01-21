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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.adhoc;

import java.util.List;

import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.JID;

public interface AdHocScriptCommandManager {

	List<Element> getCommandListItems(final JID senderJid, final JID toJid);

	boolean canCallCommand(JID jid, String commandId);

	List<Packet> process(Packet packet);

}
