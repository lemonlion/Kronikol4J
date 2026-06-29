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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
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

    // Self-managed via Testcontainers by default; an explicit -Dkron.redis.endpoint=host:port overrides it
    // (e.g. for an externally-provided Redis). Skips gracefully when no Docker/Redis is reachable, keeping the
    // suite green on machines without a container engine.
    private static GenericContainer<?> container;
    private static Jedis raw;

    @BeforeAll
    static void connectRedis() {
        String endpoint = System.getProperty("kron.redis.endpoint");
        if (endpoint == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "No Docker available and no -Dkron.redis.endpoint set — skipping the live-Redis parity test");
            container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
            container.start();
            endpoint = container.getHost() + ":" + container.getMappedPort(6379);
        }
        String[] hp = endpoint.split(":");
        try {
            raw = new Jedis(hp[0], Integer.parseInt(hp[1]));
            raw.ping();
            raw.flushDB();
        } catch (RuntimeException unreachable) {
            Assumptions.abort("No Redis reachable at " + endpoint + ": " + unreachable);
        }
    }

    @AfterAll
    static void stop() {
        if (raw != null) {
            raw.close();
        }
        if (container != null) {
            container.stop();
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

        // (2) REQUEST content is byte-identical on EVERY line — incl. the write payloads Set="v" and
        //     HashSet="f=v" (the gap this end-to-end check first surfaced, now fixed in JedisCommandsTracker).
        for (int i = 0; i < golden.size(); i++) {
            if (golden.get(i)[0].equals("Request")) {
                assertThat(actual.get(i)[3]).as("line " + i + " request content (" + golden.get(i)[1] + ")")
                    .isEqualTo(golden.get(i)[3]);
            }
        }

        // (3) READ + INCREMENT response content is byte-identical (client-independent values).
        for (int i = 0; i < golden.size(); i++) {
            String label = golden.get(i)[1];
            if (golden.get(i)[0].equals("Response")
                && (label.startsWith("Get") || label.startsWith("HashGet") || label.equals("Increment"))) {
                assertThat(actual.get(i)[3]).as("line " + i + " response content").isEqualTo(golden.get(i)[3]);
            }
        }

        // (4) Remaining divergence is WRITE-op RESPONSE content only — an inherent client return-type
        //     difference (StackExchange returns bool; Jedis returns the status string / affected-count), not a
        //     Kronikol bug. Pinned so a regression (or an accidental convergence) is caught, not hidden:
        //       Set     response: .NET "True"  vs Java "OK"   (bool vs status)
        //       Delete  response: .NET "True"  vs Java "1"    (bool vs deleted-count)
        //       HashSet response: .NET "True"  vs Java "1"    (bool vs added-count)
        assertThat(contentFor(actual, "Set", "Response")).isEqualTo("OK");
        assertThat(contentFor(golden, "Set", "Response")).isEqualTo("True");
        assertThat(contentFor(actual, "Delete", "Response")).isEqualTo("1");
        assertThat(contentFor(golden, "Delete", "Response")).isEqualTo("True");
        assertThat(contentFor(actual, "HashSet", "Response")).isEqualTo("1");
        assertThat(contentFor(golden, "HashSet", "Response")).isEqualTo("True");
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
