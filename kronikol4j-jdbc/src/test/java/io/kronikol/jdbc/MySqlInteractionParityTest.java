package io.kronikol.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end cross-runtime capture parity for MySQL: drives the <em>same generic JDBC adapter</em>
 * ({@link TrackingDataSource}, the one already proven against Postgres) against a live MySQL (Testcontainers)
 * and diffs the emitted {@link RequestResponseLog}s against the captured output of the <em>real .NET
 * MySqlConnector adapter</em> (fixture {@code mysql-interactions.txt}, harness {@code KRON_MYSQL_E2E=1}). The
 * env-specific {@code host} in the URI is normalised to {@code HOST}; the Java side is configured with
 * {@code uriScheme=mysql} to match the .NET {@code MySqlTrackingOptions} default.
 *
 * <p><strong>Result:</strong> byte-identical on EVERY line and EVERY field — operation+table label,
 * {@code mysql://HOST/test/orders} request URI, {@code mysql:///} response URI, status, request SQL text, AND
 * the response row summaries ({@code 0 rows affected} for the DDL, {@code 1 rows affected}, {@code 1 row
 * [id, name]}). Unlike Postgres (where ADO.NET's {@code ExecuteNonQuery} returns {@code -1} for DDL vs JDBC's
 * {@code 0}), MySqlConnector returns {@code 0} for DDL too — so MySQL has <em>no</em> divergence at all.
 */
class MySqlInteractionParityTest {

    private static GenericContainer<?> mysql;
    private static DataSource tracked;
    private static DataSource raw;

    @BeforeAll
    static void startMySql() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
            "No Docker available — skipping the live-MySQL parity test");
        mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
            .withExposedPorts(3306)
            .withEnv("MYSQL_ROOT_PASSWORD", "pw")
            .withEnv("MYSQL_DATABASE", "test")
            .withEnv("MYSQL_USER", "kron")
            .withEnv("MYSQL_PASSWORD", "pw")
            .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2)
                .withStartupTimeout(java.time.Duration.ofMinutes(3)));
        mysql.start();
        MysqlDataSource ds = new MysqlDataSource();
        ds.setServerName(mysql.getHost());
        try {
            ds.setPortNumber(mysql.getMappedPort(3306));
            ds.setDatabaseName("test");
            ds.setUser("kron");
            ds.setPassword("pw");
            ds.setAllowPublicKeyRetrieval(true); // MySQL 8 caching_sha2_password over a plaintext test channel
            ds.setUseSSL(false);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        raw = ds;
        tracked = TrackingDataSource.wrap(ds, SqlTrackingOptions.builder()
            .serviceName("OrdersDb").uriScheme("mysql").build());
        awaitConnectable(ds); // the "ready for connections" log also fires for MySQL's init server — poll TCP auth
    }

    /** Polls a real JDBC connection until the post-init MySQL server actually accepts auth (up to ~60s). */
    private static void awaitConnectable(DataSource ds) {
        java.sql.SQLException last = null;
        for (int i = 0; i < 30; i++) {
            try (Connection c = ds.getConnection()) {
                return;
            } catch (java.sql.SQLException e) {
                last = e;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
        throw new IllegalStateException("MySQL never became connectable", last);
    }

    @AfterAll
    static void stop() {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void javaMySqlCaptureMatchesDotNetByteForByte() throws Exception {
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
        List<String[]> golden = parse(readResource("/parity/mysql-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        // Every field on every line is byte-identical cross-runtime — including the DDL response (both 0).
        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + (g[1].isEmpty() ? "Response" : g[1]) + ")";
            assertThat(a).as(where).containsExactly(g);
        }
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
        try (InputStream in = MySqlInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
