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
package tigase.pubsub.repository;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.DataRepositoryPool;

import java.util.Collections;

/**
 * Created by andrzej on 23.02.2016.
 */
public class JDBCPubSubDAOTest
		extends AbstractPubSubDAOTest<DataRepository> {

	private static final String PROJECT_ID = "pubsub";
	private static final String VERSION = "4.0.0";

	@ClassRule
	public static TestRule rule = new TestRule() {
		@Override
		public Statement apply(Statement stmnt, Description d) {
			if (uri == null || !uri.startsWith("jdbc:")) {
				return new Statement() {
					@Override
					public void evaluate() throws Throwable {
						Assume.assumeTrue("Ignored due to not passed DB URI!", false);
					}
				};
			}
			return stmnt;
		}
	};

	@BeforeClass
	public static void prepareTest() throws DBInitException {
		loadSchema(PROJECT_ID, VERSION, Collections.singleton("pubsub"));
		if (uri.contains(":sqlserver:")) {
			no_of_connections = 2;
		}
	}
	
	// We need at least 2 for SQLServer
	private static int no_of_connections = 1;

	@Override
	protected String getMAMID(Object nodeId, String itemId) {
		return nodeId.toString() + "," + itemId;
	}

	@Override
	protected DataRepository prepareDataSource()
			throws DBInitException, IllegalAccessException, InstantiationException {
		DataRepositoryPool pool = new DataRepositoryPool();
		pool.initialize(uri);
		for (int i = 0; i < no_of_connections; i++) {
			pool.addRepo(super.prepareDataSource());
		}
		return pool;
	}
}
