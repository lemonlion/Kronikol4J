package io.kronikol.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cross-runtime byte-parity for the Redis capture logic: the Java {@link RedisOperationClassifier} is diffed
 * against the <em>real .NET</em> {@code RedisOperationClassifier} output (the {@code redis-classification.txt}
 * fixture was captured by driving the actual .NET classifier in {@code parity-harness/dotnet-capture}). Hardens
 * the cache hit/miss + operation-label logic from "unit-proven against the spec" to "byte-proven against .NET's
 * actual output" — same battery, same projection (DETAILED + SUMMARISED diagram labels), no live Redis required.
 */
class RedisClassificationParityTest {

    /** The identical battery the .NET harness drives: {command, hasResult, key, db}. */
    private static final List<Object[]> BATTERY = List.of(
        c("GET", true, "user:1", 0),  c("GET", false, "user:2", 0),
        c("SET", true, "user:1", 0),  c("SETEX", true, "k", 0),
        c("DEL", true, "k", 0),       c("HGET", true, "h", 0),
        c("HSET", true, "h", 0),      c("HGETALL", true, "h", 2),
        c("INCR", true, "c", 0),      c("EXPIRE", true, "k", 0),
        c("EXISTS", true, "k", 0),    c("MGET", true, "k", 0),
        c("PING", true, null, 0),     c("SUBSCRIBE", true, "ch", 0),
        c("BOGUSCMD", true, "x", 0));

    @Test
    void javaClassifierMatchesDotNetByteForByte() throws IOException {
        assertThat(render()).isEqualTo(readResource("/parity/redis-classification.txt"));
    }

    private static String render() {
        StringBuilder sb = new StringBuilder();
        for (Object[] r : BATTERY) {
            String cmd = (String) r[0];
            boolean hasResult = (boolean) r[1];
            String key = (String) r[2];
            int db = (int) r[3];
            RedisOperationInfo info = RedisOperationClassifier.classify(cmd, hasResult, key, db);
            sb.append("cmd=").append(cmd).append('\n');
            sb.append("hasResult=").append(hasResult).append('\n');
            sb.append("key=").append(key == null ? "~null~" : key).append('\n');
            sb.append("db=").append(db).append('\n');
            sb.append("detailed=").append(label(info, TrackingVerbosity.DETAILED)).append('\n');
            sb.append("summarised=").append(label(info, TrackingVerbosity.SUMMARISED)).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String label(RedisOperationInfo info, TrackingVerbosity v) {
        String s = RedisOperationClassifier.getDiagramLabel(info, v);
        return s == null ? "~null~" : s;
    }

    private static Object[] c(String cmd, boolean hasResult, String key, int db) {
        return new Object[] {cmd, hasResult, key, db};
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = RedisClassificationParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
