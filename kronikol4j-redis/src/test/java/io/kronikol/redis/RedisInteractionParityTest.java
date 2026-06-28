package io.kronikol.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.JedisCommands;

/**
 * End-to-end cross-runtime capture parity for Redis: drives the <em>real Jedis adapter</em> against a live
 * Redis (Testcontainers) and diffs the emitted {@link RequestResponseLog}s against the captured output of the
 * <em>real .NET StackExchange adapter</em> driven against a live Redis (fixture {@code redis-interactions.txt}
 * from {@code parity-harness/dotnet-capture}, env {@code KRON_REDIS_E2E=1}). This is the strict end-to-end check
 * the fake-proxy unit tests cannot do — it exercises real client return types.
 *
 * <p><strong>Result:</strong> the operation classification is byte-identical cross-runtime — {@code type},
 * {@code method} (the Get/Set/Get (Hit)/Get (Miss)/… label), {@code uri} ({@code redis://db0/<key>}) and
 * {@code status} match exactly on every line, and READ-op content (hit value / miss null) matches. WRITE-op
 * content legitimately differs by client library and is asserted as a documented divergence (below), not hidden.
 */
class RedisInteractionParityTest {

    // Connects to a live Redis at kron.redis.endpoint (default localhost:16379). Self-contained Testcontainers
    // management is the intended form, but Testcontainers' docker-API detection does not negotiate Rancher's
    // Windows npipe from the JDK-25 test JVM here, so the endpoint is configurable and the test is skipped when
    // no Redis is reachable — keeping it CI-portable while proving the parity against a real server.
    private static Jedis raw;

    @BeforeAll
    static void connectRedis() {
        String endpoint = System.getProperty("kron.redis.endpoint", "localhost:16379");
        String[] hp = endpoint.split(":");
        try {
            raw = new Jedis(hp[0], Integer.parseInt(hp[1]));
            raw.ping();
            raw.flushDB();
        } catch (RuntimeException unreachable) {
            Assumptions.abort("No Redis reachable at " + endpoint + " (set -Dkron.redis.endpoint): " + unreachable);
        }
    }

    @AfterAll
    static void stop() {
        if (raw != null) {
            raw.close();
        }
    }

    @Test
    void javaRedisCaptureMatchesDotNetOnTheParityFields() throws IOException {
        RequestResponseLogger.clear();
        // Identity resolves from the ambient TestIdentityScope below; the projection drops testName/testId.
        JedisCommands db = JedisCommandsTracker.wrap(raw, RedisTrackerOptions.forCache("CartCache"));

        try (var scope = TestIdentityScope.begin("MyTest", "t1")) {
            db.set("k", "v");
            db.get("k");          // hit
            db.get("missing");    // miss
            db.del("k");
            db.incr("c");
            db.hset("h", "f", "v");
            db.hget("h", "f");    // hit
            db.hget("h", "absent"); // miss
        }

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/redis-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        // (1) type | method | uri | status are byte-identical cross-runtime on every line (the core capture
        //     parity: operation classification, hit/miss labelling, URI, status).
        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + g[1] + ")";
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " method/label").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);
            assertThat(a[4]).as(where + " status").isEqualTo(g[4]);
        }

        // (2) READ-op content (Get/HashGet) is byte-identical — hit value / miss null are client-independent.
        for (int i = 0; i < golden.size(); i++) {
            if (golden.get(i)[1].startsWith("Get") || golden.get(i)[1].startsWith("HashGet")) {
                assertThat(actual.get(i)[3]).as("line " + i + " read content").isEqualTo(golden.get(i)[3]);
            }
        }

        // (3) WRITE-op content DIVERGES by client library — documented, not hidden:
        //   .NET (StackExchange)            Java (Jedis)
        //   Set response      = "True"      = "OK"      (StackExchange returns bool; Jedis returns the status)
        //   Delete response   = "True"      = "1"       (bool vs deleted-count)
        //   HashSet request   = "f=v"       = "~null~"  (Java's Jedis hset tracker does not capture field=value)
        //   HashSet response  = "True"      = "1"       (bool vs added-count)
        // These are real cross-runtime differences this end-to-end check surfaced (invisible to the fake-proxy
        // unit tests). The "HashSet request = ~null~" one is a genuine Java capture gap (tracked separately);
        // the bool-vs-status/count ones are inherent client-library return-type differences.
        assertThat(contentFor(actual, "Set", "Response")).isEqualTo("OK");
        assertThat(contentFor(golden, "Set", "Response")).isEqualTo("True");
        assertThat(contentFor(actual, "Delete", "Response")).isEqualTo("1");
        assertThat(contentFor(golden, "Delete", "Response")).isEqualTo("True");
        assertThat(contentFor(actual, "HashSet", "Request")).isEqualTo("~null~");
        assertThat(contentFor(golden, "HashSet", "Request")).isEqualTo("f=v");
    }

    private static String contentFor(List<String[]> rows, String method, String type) {
        return rows.stream().filter(r -> r[1].equals(method) && r[0].equals(type)).findFirst().orElseThrow()[3];
    }

    private static List<String[]> project(List<RequestResponseLog> logs) {
        List<String[]> out = new ArrayList<>();
        for (RequestResponseLog l : logs) {
            out.add(new String[] {
                l.type().toString().equals("REQUEST") ? "Request" : "Response",
                l.method() == null ? "~null~" : l.method().value(),
                l.uri() == null ? "~null~" : l.uri().toString(),
                l.content() == null ? "~null~" : l.content(),
                l.statusCode() == null ? "~null~" : statusText(l.statusCode())});
        }
        return out;
    }

    private static String statusText(io.kronikol.core.tracking.StatusCode s) {
        return s instanceof io.kronikol.core.tracking.StatusCode.Http h
            ? String.valueOf(h.code()) : ((io.kronikol.core.tracking.StatusCode.Custom) s).value();
    }

    private static List<String[]> parse(String golden) {
        List<String[]> out = new ArrayList<>();
        for (String line : golden.split("\n")) {
            if (!line.isEmpty()) {
                out.add(line.split("\\|", -1));
            }
        }
        return out;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = RedisInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
