/*
 * JDBCPubSubDAOTest.java
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
package tigase.pubsub.repository;

import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.DataRepositoryPool;
import tigase.db.util.SchemaLoader;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by andrzej on 23.02.2016.
 */
public class JDBCPubSubDAOTest extends AbstractPubSubDAOTest<DataRepository> {

	private static final String PROJECT_ID = "pubsub";
	private static final String VERSION = "4.0.0";

	// We need at least 2 for SQLServer
	private static int no_of_connections = 1;

	@ClassRule
	public static TestRule rule = new TestRule() {
		@Override
		public Statement apply(Statement stmnt, Description d) {
			if (uri == null) {
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
	public static void loadSchema() {
		if (uri.startsWith("jdbc:")) {
			SchemaLoader loader = SchemaLoader.newInstance("jdbc");
			SchemaLoader.Parameters params = loader.createParameters();
			params.parseUri(uri);
			params.setDbRootCredentials(null, null);
			loader.init(params);
			loader.validateDBConnection();
			loader.validateDBExists();
			Assert.assertEquals(SchemaLoader.Result.ok, loader.loadSchema(PROJECT_ID, VERSION));
			loader.shutdown();
			if (uri.contains(":sqlserver:")) {
				no_of_connections = 2;
			}
		}
	}

	@AfterClass
	public static void cleanDerby() {
		if (uri.contains("jdbc:derby:")) {
			File f = new File("derby_test");
			if (f.exists()) {
				if (f.listFiles() != null) {
					Arrays.asList(f.listFiles()).forEach(f2 -> {
						if (f2.listFiles() != null) {
							Arrays.asList(f2.listFiles()).forEach(f3 -> f3.delete());
						}
						f2.delete();
					});
				}
				f.delete();
			}
		}
	}

	@Override
	protected String getMAMID(Object nodeId, String itemId) {
		return nodeId.toString() + "," + itemId;
	}

	@Override
	protected DataRepository prepareDataSource() throws DBInitException, IllegalAccessException, InstantiationException {
		DataRepositoryPool pool = new DataRepositoryPool();
		pool.initRepository(uri, new HashMap());
		for (int i=0; i<no_of_connections; i++) {
			pool.addRepo(super.prepareDataSource());
		}
		return pool;
	}
}
