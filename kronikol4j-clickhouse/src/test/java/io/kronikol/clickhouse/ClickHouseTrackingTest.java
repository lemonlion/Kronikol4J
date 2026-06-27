package io.kronikol.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.jdbc.SqlTrackingOptions;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the ClickHouse tracking convenience layer applies the ClickHouse defaults (service name,
 * {@code ClickHouse} category → database shape, {@code clickhouse://} URI scheme) over the shared JDBC
 * plumbing, proven end-to-end against in-memory H2 (the wrapper is driver-agnostic).
 */
class ClickHouseTrackingTest {

    private DataSource tracked;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:clickhousetest;DB_CLOSE_DELAY=-1;MODE=MySQL");
        SqlTrackingOptions options = ClickHouseTracking.options()
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build();
        tracked = ClickHouseTracking.wrap(h2, options);

        try (Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS events");
            s.executeUpdate("CREATE TABLE events (id INT PRIMARY KEY, name VARCHAR(50))");
        }
        RequestResponseLogger.clear();
    }

    @AfterEach
    void tearDown() {
        RequestResponseLogger.clear();
    }

    @Test
    void defaultsAreClickHouseFlavoured() {
        SqlTrackingOptions o = ClickHouseTracking.defaultOptions();
        assertThat(o.serviceName()).isEqualTo("ClickHouse");
        assertThat(o.dependencyCategory()).isEqualTo(DependencyCategories.CLICK_HOUSE);
        assertThat(o.uriScheme()).isEqualTo("clickhouse");
    }

    @Test
    void recordsAClickHouseInteractionWithTheRightCategoryAndUriScheme() throws Exception {
        try (Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO events (id, name) VALUES (1, 'login')");
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("INSERT INTO events");
        assertThat(req.serviceName()).isEqualTo("ClickHouse");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.CLICK_HOUSE);
        assertThat(req.uri().toString()).startsWith("clickhouse://"); // ClickHouse scheme, not sql://

        // Renders as a database participant (the ClickHouse category resolves to the database shape).
        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("database \"ClickHouse\" as clickHouse");
    }
}
