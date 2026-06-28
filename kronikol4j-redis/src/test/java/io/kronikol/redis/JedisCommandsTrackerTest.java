package io.kronikol.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.commands.JedisCommands;

/**
 * Verifies the Jedis command-hook wrapper auto-captures commands issued through a {@link JedisCommands}.
 * Uses a fake {@code JedisCommands} (a dynamic proxy returning canned values) so no Redis server is needed —
 * the wrapper's interception logic (symmetric to the Lettuce {@link RedisCommandsTracker}) is under test.
 */
class JedisCommandsTrackerTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    /** A fake JedisCommands: {@code get("absent")} → null (miss), other gets hit, {@code set}→"OK". */
    private static JedisCommands fakeCommands() {
        return (JedisCommands) Proxy.newProxyInstance(
            JedisCommands.class.getClassLoader(),
            new Class<?>[] {JedisCommands.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "get" -> "absent".equals(args[0]) ? null : "cached-value";
                case "set" -> "OK";
                case "hget" -> "field-value";
                case "toString" -> "fake";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }

    private static RedisTrackerOptions options() {
        return RedisTrackerOptions.builder()
            .serviceName("Cache")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build();
    }

    private static JedisCommands tracked() {
        return JedisCommandsTracker.wrap(fakeCommands(), options(), "localhost");
    }

    @Test
    void getHitIsCaptured() {
        String value = tracked().get("user:1");
        assertThat(value).isEqualTo("cached-value");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("Get");
        assertThat(req.uri().toString()).isEqualTo("redis://db0/user:1");
        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.method().value()).isEqualTo("Get (Hit)");
    }

    @Test
    void getMissIsCaptured() {
        assertThat(tracked().get("absent")).isNull();
        assertThat(RequestResponseLogger.getAllLogs().get(1).method().value()).isEqualTo("Get (Miss)");
    }

    @Test
    void setIsCaptured() {
        assertThat(tracked().set("k", "v")).isEqualTo("OK");
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("Set");
        assertThat(logs.get(0).uri().toString()).isEqualTo("redis://db0/k");
    }

    @Test
    void databaseNumberFromWrapIsUsedInUri() {
        // The Jedis keyed-command interface has no SELECT; the db is fixed per connection via wrap(...).
        JedisCommands redis = JedisCommandsTracker.wrap(fakeCommands(), options(), "localhost", 3);
        redis.get("user:1");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).uri().toString()).isEqualTo("redis://db3/user:1");
    }

    @Test
    void objectMethodsAreNotTracked() {
        JedisCommands redis = tracked();
        redis.toString();
        redis.hashCode();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
