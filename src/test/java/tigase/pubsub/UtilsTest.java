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

import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class UtilsTest {

    @Ignore
    @Test
    public void test() throws NoSuchAlgorithmException {
        //Utils.Spi spi = new Utils.Spi(SecureRandom.getInstance("SHA1PRNG"));
        Utils.Spi spi = new Utils.Spi(new Random());
        List<byte[]> list = new ArrayList<>();
        for (int i=0; i<1000; i++) {
            byte[] bytes = new byte[13];
            spi.engineNextBytes(bytes, 1);
            list.add(bytes);
        }
        for (int i=0; i<1000; i++) {
            for (int j=0; j<1000; j++) {
                if (i == j) {
                    continue;
                }
                String x1 = (new BigInteger(list.get(i))).toString(36);
                String x2 = (new BigInteger(list.get(j))).toString(36);
                assertNotEquals("At possitions " + i + ", " + j + "found collision!", x1, x2);
            }
        }
    }

    @Ignore
    @Test
    public void concurrencyTest() throws NoSuchAlgorithmException, InterruptedException {
        Utils.Spi spi = new Utils.Spi(new Random());
        List<Callable<byte[]>> tasks = new ArrayList<>();
        for (int i=0; i<1000; i++) {
            tasks.add(new Callable<byte[]>() {
                @Override
                public byte[] call() throws Exception {
                    byte[] bytes = new byte[13];
                    spi.engineNextBytes(bytes, 1);
                    return bytes;
                }
            });
        }

        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
        List<Future<byte[]>> futures = executorService.invokeAll(tasks);

        executorService.shutdown();

        while (!executorService.isTerminated()) {
            Thread.sleep(1000);
        }

        List<byte[]> list = futures.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception x) {
                throw new RuntimeException(x);
            }
        }).collect(Collectors.toList());

        for (int i=0; i<1000; i++) {
            for (int j=0; j<1000; j++) {
                if (i == j) {
                    continue;
                }
                String x1 = (new BigInteger(list.get(i))).toString(36);
                String x2 = (new BigInteger(list.get(j))).toString(36);
                assertNotEquals("At possitions " + i + ", " + j + "found collision!", x1, x2);
            }
        }
    }

}
