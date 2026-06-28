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

    @Test
    void untypedExecuteIsTrackedWithUpdateCount() throws Exception {
        try (Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            boolean hasResultSet = s.execute("INSERT INTO customers (id, name) VALUES (50, 'Dennis')");
            assertThat(hasResultSet).isFalse();
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("INSERT INTO customers");
        assertThat(logs.get(1).type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(logs.get(1).content()).isEqualTo("1 rows affected"); // read back via getUpdateCount()
    }

    @Test
    void preparedStatementBatchIsTrackedWithSummedCounts() throws Exception {
        try (Connection c = tracked.getConnection();
             var ps = c.prepareStatement("INSERT INTO customers (id, name) VALUES (?, ?)")) {
            ps.setInt(1, 60);
            ps.setString(2, "Ken");
            ps.addBatch();
            ps.setInt(1, 61);
            ps.setString(2, "Brian");
            ps.addBatch();
            int[] counts = ps.executeBatch();
            assertThat(counts).containsExactly(1, 1);
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("INSERT INTO customers");
        assertThat(logs.get(1).content()).isEqualTo("2 rows affected"); // 1 + 1 summed
    }

    // --- FULL_ROWS cell-level capture end-to-end ---

    @Test
    void fullRowsDetailCapturesCellLevelJson() throws Exception {
        DataSource fullRows = wrapFullRows(10);
        try (Connection c = fullRows.getConnection(); Statement seed = c.createStatement()) {
            seed.executeUpdate("INSERT INTO customers (id, name) VALUES (10, 'Grace')");
            seed.executeUpdate("INSERT INTO customers (id, name) VALUES (11, 'Edsger')");
        }
        RequestResponseLogger.clear();

        try (Connection c = fullRows.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name FROM customers ORDER BY id")) {
            while (rs.next()) {
                // drain — capture happens as rows are read
            }
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        // Compact cell-level JSON (H2 upper-cases the labels); integers unquoted, strings quoted.
        assertThat(logs.get(1).content())
            .isEqualTo("[{\"ID\":10,\"NAME\":\"Grace\"},{\"ID\":11,\"NAME\":\"Edsger\"}]");
    }

    @Test
    void fullRowsDetailTruncatesAtMaxResponseRows() throws Exception {
        DataSource fullRows = wrapFullRows(1); // capture at most one row
        try (Connection c = fullRows.getConnection(); Statement seed = c.createStatement()) {
            seed.executeUpdate("INSERT INTO customers (id, name) VALUES (20, 'A')");
            seed.executeUpdate("INSERT INTO customers (id, name) VALUES (21, 'B')");
            seed.executeUpdate("INSERT INTO customers (id, name) VALUES (22, 'C')");
        }
        RequestResponseLogger.clear();

        try (Connection c = fullRows.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name FROM customers ORDER BY id")) {
            while (rs.next()) {
                // drain
            }
        }

        assertThat(RequestResponseLogger.getAllLogs().get(1).content())
            .isEqualTo("[{\"ID\":20,\"NAME\":\"A\"}]\n... (2 more rows not shown)");
    }

    private static DataSource wrapFullRows(int maxRows) {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:trackingtest;DB_CLOSE_DELAY=-1");
        return TrackingDataSource.wrap(h2, SqlTrackingOptions.builder()
            .serviceName("ShopDb")
            .responseDetail(SqlResponseDetail.FULL_ROWS)
            .maxResponseRows(maxRows)
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1)).build());
    }

    // --- verbosity + classifier exposed end-to-end through the DataSource (the Dapper/JdbcTemplate analog) ---

    @Test
    void rawVerbosityThroughDataSourceUsesKeywordMethodAndFullUri() throws Exception {
        javax.sql.DataSource raw = wrapWith(io.kronikol.core.tracking.TrackingVerbosity.RAW);
        try (Connection c = raw.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO customers (id, name) VALUES (40, 'Ada')");
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).method().value()).isEqualTo("INSERT");          // Raw = the raw keyword
        assertThat(logs.get(0).uri().toString()).isEqualTo("sql://localhost/TRACKINGTEST"); // host + db
        assertThat(logs.get(0).content()).isEqualTo("INSERT INTO customers (id, name) VALUES (40, 'Ada')");
    }

    @Test
    void summarisedVerbosityThroughDataSourceOmitsContentAndUsesSchemeOnlyUri() throws Exception {
        javax.sql.DataSource summarised = wrapWith(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        try (Connection c = summarised.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO customers (id, name) VALUES (41, 'Grace')");
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).method().value()).isEqualTo("INSERT");  // classifier's summarised label
        assertThat(logs.get(0).content()).isNull();                    // Summarised drops the SQL text
        assertThat(logs.get(0).uri().toString()).isEqualTo("sql:///TRACKINGTEST/customers"); // scheme-only host
    }

    private static javax.sql.DataSource wrapWith(io.kronikol.core.tracking.TrackingVerbosity verbosity) {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:trackingtest;DB_CLOSE_DELAY=-1");
        return TrackingDataSource.wrap(h2, SqlTrackingOptions.builder()
            .serviceName("ShopDb").verbosity(verbosity)
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1)).build());
    }
}
