package io.kronikol.jdbc;

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
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end cross-runtime capture parity for SQL: drives the <em>real JDBC adapter</em>
 * ({@link TrackingDataSource}) against a live Postgres (Testcontainers) and diffs the emitted
 * {@link RequestResponseLog}s against the captured output of the <em>real .NET Npgsql adapter</em> driven
 * against a live Postgres (fixture {@code sql-interactions.txt}, harness {@code KRON_PG_E2E=1}). The
 * env-specific {@code host:port} in the URI is normalised to {@code HOST}; the Java side is configured with
 * {@code uriScheme=postgresql} to match Npgsql.
 */
class SqlInteractionParityTest {

    private static GenericContainer<?> pg;
    private static DataSource tracked;
    private static DataSource raw;

    @BeforeAll
    static void startPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
            "No Docker available — skipping the live-Postgres parity test");
        pg = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "test")
            .withEnv("POSTGRES_USER", "kron")
            .withEnv("POSTGRES_PASSWORD", "pw");
        pg.start();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[] {pg.getHost()});
        ds.setPortNumbers(new int[] {pg.getMappedPort(5432)});
        ds.setDatabaseName("test");
        ds.setUser("kron");
        ds.setPassword("pw");
        raw = ds;
        tracked = TrackingDataSource.wrap(ds, SqlTrackingOptions.builder()
            .serviceName("OrdersDb").uriScheme("postgresql").build());
    }

    @AfterAll
    static void stop() {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (pg != null) {
            pg.stop();
        }
    }

    @Test
    void javaSqlCaptureMatchesDotNetOnTheParityFields() throws Exception {
        try (Connection c = raw.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS orders"); // clean start, untracked
        }
        RequestResponseLogger.clear();
        try (var scope = TestIdentityScope.begin("MyTest", "t1");
             Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE orders (id INT, name TEXT)");
            s.executeUpdate("INSERT INTO orders (id, name) VALUES (1, 'a')");
            try (ResultSet r = s.executeQuery("SELECT * FROM orders WHERE id = 1")) {
                while (r.next()) {
                    // drain
                }
            }
            s.executeUpdate("UPDATE orders SET name = 'b' WHERE id = 1");
            s.executeUpdate("DELETE FROM orders WHERE id = 1");
        }

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/sql-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        // type | method(label) | uri | status are byte-identical cross-runtime on every line — the core capture
        // parity (operation+table classification, scheme/db/table request URI, response URI, status). CONTENT is
        // byte-identical too on every line — request SQL text AND response row summaries ("1 rows affected",
        // "1 row [id, name]") — EXCEPT the one cell at index 1 (the CREATE TABLE / DDL response), an inherent
        // driver-API convention difference handled below.
        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + (g[1].isEmpty() ? "Response" : g[1]) + ")";
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " method").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);
            assertThat(a[4]).as(where + " status").isEqualTo(g[4]);
            if (i != 1) {
                assertThat(a[3]).as(where + " content").isEqualTo(g[3]);
            }
        }

        // The only divergence end-to-end: the DDL (CREATE TABLE) affected-rows count. ADO.NET's
        // ExecuteNonQuery returns -1 for DDL; JDBC's executeUpdate returns 0. An inherent driver-API
        // convention difference (not a Kronikol bug) — pinned so a regression or accidental change is caught.
        assertThat(actual.get(1)[3]).as("DDL response (JDBC)").isEqualTo("0 rows affected");
        assertThat(golden.get(1)[3]).as("DDL response (.NET)").isEqualTo("-1 rows affected");
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
        try (InputStream in = SqlInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
