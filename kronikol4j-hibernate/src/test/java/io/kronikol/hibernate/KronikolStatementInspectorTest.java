package io.kronikol.hibernate;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.jdbc.SqlTrackingOptions;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Hibernate {@link KronikolStatementInspector} records each inspected statement as a tracked
 * request/response pair (reusing the JDBC recorder + the shared SQL classifier) and returns the SQL unchanged.
 */
class KronikolStatementInspectorTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static SqlTrackingOptions opts() {
        return SqlTrackingOptions.builder()
            .serviceName("ShopDb")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build();
    }

    @Test
    void inspectRecordsAClassifiedRequestResponsePairAndReturnsSqlUnchanged() {
        KronikolStatementInspector inspector =
            new KronikolStatementInspector(opts(), "db-host", "shopdb");

        String sql = "SELECT * FROM Customers WHERE id = 1";
        String returned = inspector.inspect(sql);

        assertThat(returned).isEqualTo(sql); // never rewrites

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("SELECT FROM Customers");   // classified label
        assertThat(req.content()).isEqualTo(sql);
        assertThat(req.uri().toString()).isEqualTo("sql://db-host/shopdb/Customers");
        assertThat(req.serviceName()).isEqualTo("ShopDb");

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isEqualTo(io.kronikol.core.tracking.StatusCode.of("OK"));
        assertThat(res.content()).isNull(); // no row count available from a StatementInspector
        // shared correlation
        assertThat(res.traceId()).isEqualTo(req.traceId());
        assertThat(res.requestResponseId()).isEqualTo(req.requestResponseId());
    }

    @Test
    void rendersADatabaseInteractionInTheDiagram() {
        KronikolStatementInspector inspector = new KronikolStatementInspector(opts(), "db-host", "shopdb");
        inspector.inspect("UPDATE Orders SET status = 'shipped' WHERE id = 7");

        String uml = PlantUmlCreator.create(RequestResponseLogger.getAllLogs()).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("database \"ShopDb\" as shopDb")     // SQL category → database shape
            .contains("UPDATE Orders");                     // classified operation label
    }

    @Test
    void doesNotTrackWithoutTestIdentity() {
        SqlTrackingOptions noIdentity = SqlTrackingOptions.builder().serviceName("ShopDb")
            .ids(IdGenerator.seeded(1)).build();
        KronikolStatementInspector inspector = new KronikolStatementInspector(noIdentity, "h", "db");

        String returned = inspector.inspect("SELECT 1");

        assertThat(returned).isEqualTo("SELECT 1");
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void passesThroughNullOrBlankSqlUntracked() {
        KronikolStatementInspector inspector = new KronikolStatementInspector(opts(), "h", "db");
        assertThat(inspector.inspect(null)).isNull();
        assertThat(inspector.inspect("   ")).isEqualTo("   ");
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
