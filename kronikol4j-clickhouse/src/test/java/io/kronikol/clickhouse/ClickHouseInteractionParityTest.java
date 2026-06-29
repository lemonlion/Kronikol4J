package io.kronikol.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end cross-runtime capture parity for ClickHouse: drives the <em>same generic JDBC adapter</em>
 * ({@link io.kronikol.jdbc.TrackingDataSource} via {@link ClickHouseTracking}) against a live ClickHouse
 * (Testcontainers) and diffs the emitted {@link RequestResponseLog}s against the captured output of the
 * <em>real .NET ClickHouse adapter</em> ({@code TrackingClickHouseConnection}, fixture
 * {@code clickhouse-interactions.txt}, harness {@code KRON_CH_E2E=1}). The env-specific {@code host} is
 * normalised to {@code HOST}; the Java side uses the {@code clickhouse} URI scheme to match the .NET
 * {@code ClickHouseTrackingOptions} default.
 *
 * <p><strong>Result:</strong> byte-identical on type, operation+table label,
 * {@code clickhouse://HOST/default/orders} request URI, {@code clickhouse:///} response URI, status, request
 * SQL text, and the SELECT response summary ({@code 1 row [id, name]}). The one divergence is pinned: the
 * INSERT affected-rows — .NET's ClickHouse.Client {@code ExecuteNonQuery} returns {@code 0}, whereas the
 * ClickHouse JDBC driver's {@code executeUpdate} returns {@code 1} — an inherent driver-API convention
 * difference, not a Kronikol bug.
 */
class ClickHouseInteractionParityTest {

    private static GenericContainer<?> clickhouse;
    private static DataSource tracked;
    private static DataSource raw;

    @BeforeAll
    static void startClickHouse() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
            "No Docker available — skipping the live-ClickHouse parity test");
        clickhouse = new GenericContainer<>(DockerImageName.parse("clickhouse/clickhouse-server:24.8"))
            .withExposedPorts(8123)
            .withEnv("CLICKHOUSE_SKIP_USER_SETUP", "1") // leave the default user passwordless (matches the harness)
            .waitingFor(Wait.forHttp("/ping").forPort(8123).forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
        clickhouse.start();
        String url = "jdbc:clickhouse://" + clickhouse.getHost() + ":" + clickhouse.getMappedPort(8123) + "/default";
        Properties props = new Properties();
        props.setProperty("user", "default");
        props.setProperty("password", "");
        props.setProperty("compress", "false"); // avoid the optional LZ4 native lib on the test classpath
        raw = new com.clickhouse.jdbc.ClickHouseDataSource(url, props);
        tracked = ClickHouseTracking.wrap(raw, ClickHouseTracking.options().serviceName("OrdersDb").build());
    }

    @AfterAll
    static void stop() {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (clickhouse != null) {
            clickhouse.stop();
        }
    }

    @Test
    void javaClickHouseCaptureMatchesDotNetOnTheParityFields() throws Exception {
        try (Connection c = raw.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS orders"); // clean start, untracked
        }
        RequestResponseLogger.clear();
        try (var scope = TestIdentityScope.begin("MyTest", "t1");
             Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE orders (id Int32, name String) ENGINE = MergeTree ORDER BY id");
            s.executeUpdate("INSERT INTO orders (id, name) VALUES (1, 'a')");
            try (ResultSet r = s.executeQuery("SELECT * FROM orders WHERE id = 1")) {
                while (r.next()) {
                    // drain
                }
            }
        }

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/clickhouse-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        // type | method(label) | uri | status are byte-identical cross-runtime on every line, and content is
        // byte-identical too — request SQL text and the SELECT response summary — EXCEPT the INSERT response
        // (index 3, the affected-rows count), an inherent driver-API convention difference handled below.
        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + (g[1].isEmpty() ? "Response" : g[1]) + ")";
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " method").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);
            assertThat(a[4]).as(where + " status").isEqualTo(g[4]);
            if (i != 3) {
                assertThat(a[3]).as(where + " content").isEqualTo(g[3]);
            }
        }

        // The only divergence end-to-end: the INSERT affected-rows count. ClickHouse.Client's ExecuteNonQuery
        // returns 0; the ClickHouse JDBC driver's executeUpdate returns 1. An inherent driver-API convention
        // difference (not a Kronikol bug) — pinned so a regression or accidental change is caught.
        assertThat(actual.get(3)[3]).as("INSERT response (JDBC)").isEqualTo("1 rows affected");
        assertThat(golden.get(3)[3]).as("INSERT response (.NET ClickHouse.Client)").isEqualTo("0 rows affected");
    }

    private static List<String[]> project(List<RequestResponseLog> logs) {
        List<String[]> out = new ArrayList<>();
        for (RequestResponseLog l : logs) {
            String uri = l.uri() == null ? "~null~" : l.uri().toString().replaceFirst("^([a-z]+://)[^/]+", "$1HOST");
            out.add(new String[] {
                l.type().toString().equals("REQUEST") ? "Request" : "Response",
                l.method() == null ? "~null~" : l.method().value(),
                uri,
                l.content() == null ? "~null~" : l.content(),
                l.statusCode() == null ? "~null~" : statusText(l.statusCode())});
        }
        return out;
    }

    private static String statusText(StatusCode s) {
        return s instanceof StatusCode.Http h ? String.valueOf(h.code()) : ((StatusCode.Custom) s).value();
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
        try (InputStream in = ClickHouseInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
