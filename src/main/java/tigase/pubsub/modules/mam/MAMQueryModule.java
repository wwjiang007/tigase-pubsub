/*
 * MAMQueryModule.java
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
package tigase.pubsub.modules.mam;

import tigase.kernel.beans.Bean;
import tigase.pubsub.PubSubComponent;
import tigase.xmpp.mam.modules.QueryModule;

/**
 * Created by andrzej on 28.12.2016.
 */
@Bean(name = "mamQueryModule", parent = PubSubComponent.class, active = true)
public class MAMQueryModule extends QueryModule {

}
