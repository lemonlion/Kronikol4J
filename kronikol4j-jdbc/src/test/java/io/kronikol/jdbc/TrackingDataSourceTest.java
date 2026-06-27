package io.kronikol.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end JDBC capture against a real in-memory H2 database: wrapping a {@link DataSource} auto-records
 * each execution as a request/response pair through {@link SqlInteractionRecorder}.
 */
class TrackingDataSourceTest {

    private DataSource tracked;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:trackingtest;DB_CLOSE_DELAY=-1");
        SqlTrackingOptions options = SqlTrackingOptions.builder()
            .serviceName("ShopDb")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build();
        tracked = TrackingDataSource.wrap(h2, options);

        try (Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS customers");
            s.executeUpdate("CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(50))");
        }
        RequestResponseLogger.clear();
    }

    @AfterEach
    void tearDown() {
        RequestResponseLogger.clear();
    }

    @Test
    void executeUpdateRecordsRequestAndRowCount() throws Exception {
        try (Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            int rows = s.executeUpdate("INSERT INTO customers (id, name) VALUES (1, 'Ada')");
            assertThat(rows).isEqualTo(1);
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(logs.get(0).method().value()).isEqualTo("INSERT INTO customers");
        assertThat(logs.get(0).content()).isEqualTo("INSERT INTO customers (id, name) VALUES (1, 'Ada')");
        assertThat(logs.get(0).serviceName()).isEqualTo("ShopDb");

        assertThat(logs.get(1).type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(logs.get(1).content()).isEqualTo("1 rows affected");
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of("OK"));
        assertThat(logs.get(0).traceId()).isEqualTo(logs.get(1).traceId());
    }

    @Test
    void executeQueryRecordsRowCountAndColumnsOnExhaustion() throws Exception {
        try (Connection c = tracked.getConnection(); Statement seed = c.createStatement()) {
            seed.executeUpdate("INSERT INTO customers (id, name) VALUES (10, 'Grace')");
            seed.executeUpdate("INSERT INTO customers (id, name) VALUES (11, 'Edsger')");
        }
        RequestResponseLogger.clear();

        try (Connection c = tracked.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name FROM customers ORDER BY id")) {
            int n = 0;
            while (rs.next()) {
                n++;
            }
            assertThat(n).isEqualTo(2);
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("SELECT FROM customers");
        assertThat(logs.get(1).type()).isEqualTo(RequestResponseType.RESPONSE);
        // H2 reports column labels upper-cased
        assertThat(logs.get(1).content()).isEqualTo("2 rows [ID, NAME]");
    }

    @Test
    void preparedStatementIsTracked() throws Exception {
        try (Connection c = tracked.getConnection();
             var ps = c.prepareStatement("INSERT INTO customers (id, name) VALUES (?, ?)")) {
            ps.setInt(1, 99);
            ps.setString(2, "Linus");
            ps.executeUpdate();
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("INSERT INTO customers");
        assertThat(logs.get(1).content()).isEqualTo("1 rows affected");
    }
}
