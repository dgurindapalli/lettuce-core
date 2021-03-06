/*
 * Copyright 2011-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.lettuce.core.cluster.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.lettuce.TestClientResources;
import io.lettuce.core.FastShutdown;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TestSettings;
import io.lettuce.core.cluster.AbstractClusterTest;
import io.lettuce.core.cluster.ClusterTestUtil;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.codec.Utf8StringCodec;
import io.lettuce.core.commands.CustomCommandTest;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.*;

/**
 * @author Mark Paluch
 */
public class CustomClusterCommandTest extends AbstractClusterTest {

    private static final Utf8StringCodec utf8StringCodec = new Utf8StringCodec();
    private static RedisClusterClient redisClusterClient;
    private StatefulRedisClusterConnection<String, String> redisClusterConnection;
    private RedisAdvancedClusterCommands<String, String> redis;

    @BeforeClass
    public static void setupClient() {
        redisClusterClient = RedisClusterClient.create(TestClientResources.get(),
                RedisURI.Builder.redis(TestSettings.host(), TestSettings.port(900)).build());
    }

    @AfterClass
    public static void closeClient() {
        FastShutdown.shutdown(redisClusterClient);
    }

    @Before
    public void openConnection() {
        redisClusterConnection = redisClusterClient.connect();
        redis = redisClusterConnection.sync();
        ClusterTestUtil.flushDatabaseOfAllNodes(redisClusterConnection);
    }

    @Test
    public void dispatchSet() {

        String response = redis.dispatch(CustomCommandTest.MyCommands.SET, new StatusOutput<>(utf8StringCodec),
                new CommandArgs<>(utf8StringCodec).addKey(key).addValue(value));

        assertThat(response).isEqualTo("OK");
    }

    @Test
    public void dispatchWithoutArgs() {

        String response = redis.dispatch(CustomCommandTest.MyCommands.INFO, new StatusOutput<>(utf8StringCodec));

        assertThat(response).contains("connected_clients");
    }

    @Test(expected = RedisCommandExecutionException.class)
    public void dispatchShouldFailForWrongDataType() {

        redis.hset(key, key, value);
        redis.dispatch(CommandType.GET, new StatusOutput<>(utf8StringCodec), new CommandArgs<>(utf8StringCodec).addKey(key));
    }

    @Test
    public void clusterAsyncPing() {

        RedisCommand<String, String, String> command = new Command<>(CustomCommandTest.MyCommands.PING, new StatusOutput<>(
                utf8StringCodec), null);

        AsyncCommand<String, String, String> async = new AsyncCommand<>(command);
        redisClusterConnection.dispatch(async);

        assertThat(async.join()).isEqualTo("PONG");
    }

    @Test
    public void clusterAsyncBatchPing() {

        RedisCommand<String, String, String> command1 = new Command<>(CustomCommandTest.MyCommands.PING, new StatusOutput<>(
                utf8StringCodec), null);

        RedisCommand<String, String, String> command2 = new Command<>(CustomCommandTest.MyCommands.PING, new StatusOutput<>(
                utf8StringCodec), null);

        AsyncCommand<String, String, String> async1 = new AsyncCommand<>(command1);
        AsyncCommand<String, String, String> async2 = new AsyncCommand<>(command2);
        redisClusterConnection.dispatch(Arrays.asList(async1, async2));

        assertThat(async1.join()).isEqualTo("PONG");
        assertThat(async2.join()).isEqualTo("PONG");
    }

    @Test
    public void clusterAsyncBatchSet() {

        RedisCommand<String, String, String> command1 = new Command<>(CommandType.SET, new StatusOutput<>(utf8StringCodec),
                new CommandArgs<>(utf8StringCodec).addKey("key1").addValue("value"));

        RedisCommand<String, String, String> command2 = new Command<>(CommandType.GET, new StatusOutput<>(utf8StringCodec),
                new CommandArgs<>(utf8StringCodec).addKey("key1"));

        RedisCommand<String, String, String> command3 = new Command<>(CommandType.SET, new StatusOutput<>(utf8StringCodec),
                new CommandArgs<>(utf8StringCodec).addKey("other-key1").addValue("value"));

        AsyncCommand<String, String, String> async1 = new AsyncCommand<>(command1);
        AsyncCommand<String, String, String> async2 = new AsyncCommand<>(command2);
        AsyncCommand<String, String, String> async3 = new AsyncCommand<>(command3);
        redisClusterConnection.dispatch(Arrays.asList(async1, async2, async3));

        assertThat(async1.join()).isEqualTo("OK");
        assertThat(async2.join()).isEqualTo("value");
        assertThat(async3.join()).isEqualTo("OK");
    }

    @Test
    public void clusterFireAndForget() {

        RedisCommand<String, String, String> command = new Command<>(CustomCommandTest.MyCommands.PING, new StatusOutput<>(
                utf8StringCodec), null);
        redisClusterConnection.dispatch(command);
        assertThat(command.isCancelled()).isFalse();

    }

    public enum MyCommands implements ProtocolKeyword {
        PING, SET, INFO;

        private final byte name[];

        MyCommands() {
            // cache the bytes for the command name. Reduces memory and cpu pressure when using commands.
            name = name().getBytes();
        }

        @Override
        public byte[] getBytes() {
            return name;
        }
    }

}
