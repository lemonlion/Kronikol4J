package io.kronikol.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.lettuce.core.api.sync.RedisCommands;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Lettuce command-hook wrapper auto-captures commands issued through a {@link RedisCommands}.
 * Uses a fake {@code RedisCommands} (a dynamic proxy returning canned values) so no Redis server is needed —
 * the wrapper's interception logic is what's under test.
 */
class RedisCommandsTrackerTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    /** A fake RedisCommands: {@code get("absent")} returns null (miss), other gets hit, set→"OK", del→1. */
    @SuppressWarnings("unchecked")
    private static RedisCommands<String, String> fakeCommands() {
        return (RedisCommands<String, String>) Proxy.newProxyInstance(
            RedisCommands.class.getClassLoader(),
            new Class<?>[] {RedisCommands.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "get" -> "absent".equals(args[0]) ? null : "cached-value";
                case "set" -> "OK";
                case "del" -> 1L;
                case "toString" -> "fake";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }

    private static RedisCommands<String, String> tracked() {
        RedisTrackerOptions options = RedisTrackerOptions.builder()
            .serviceName("Cache")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build();
        return RedisCommandsTracker.wrap(fakeCommands(), options, "localhost");
    }

    @Test
    void getHitIsCaptured() {
        RedisCommands<String, String> redis = tracked();

        String value = redis.get("user:1");
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
    }

    @Test
    void objectMethodsAreNotTracked() {
        RedisCommands<String, String> redis = tracked();
        redis.toString();
        redis.hashCode();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
