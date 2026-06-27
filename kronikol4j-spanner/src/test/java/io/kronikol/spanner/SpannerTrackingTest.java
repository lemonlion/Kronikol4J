package io.kronikol.spanner;

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
 * Verifies the Spanner tracking convenience layer applies the Spanner defaults (service name,
 * {@code Spanner} category → database shape, {@code spanner://} URI scheme) over the shared JDBC plumbing,
 * proven end-to-end against in-memory H2 (the wrapper is driver-agnostic).
 */
class SpannerTrackingTest {

    private DataSource tracked;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:spannertest;DB_CLOSE_DELAY=-1");
        SqlTrackingOptions options = SpannerTracking.options()
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build();
        tracked = SpannerTracking.wrap(h2, options);

        try (Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS singers");
            s.executeUpdate("CREATE TABLE singers (id INT PRIMARY KEY, name VARCHAR(50))");
        }
        RequestResponseLogger.clear();
    }

    @AfterEach
    void tearDown() {
        RequestResponseLogger.clear();
    }

    @Test
    void defaultsAreSpannerFlavoured() {
        SqlTrackingOptions o = SpannerTracking.defaultOptions();
        assertThat(o.serviceName()).isEqualTo("Spanner");
        assertThat(o.dependencyCategory()).isEqualTo(DependencyCategories.SPANNER);
        assertThat(o.uriScheme()).isEqualTo("spanner");
    }

    @Test
    void recordsASpannerInteractionWithTheRightCategoryAndUriScheme() throws Exception {
        try (Connection c = tracked.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO singers (id, name) VALUES (1, 'Ada')");
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("INSERT INTO singers");
        assertThat(req.serviceName()).isEqualTo("Spanner");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.SPANNER);
        assertThat(req.uri().toString()).startsWith("spanner://");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("database \"Spanner\" as spanner");
    }
}
