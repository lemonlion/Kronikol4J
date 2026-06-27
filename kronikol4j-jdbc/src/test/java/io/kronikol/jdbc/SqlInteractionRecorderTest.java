package io.kronikol.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.sql.SqlCommandType;
import io.kronikol.core.sql.UnifiedSqlOperation;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the SQL request/response log pair matches .NET {@code SqlDiagnosticTracker} across verbosity
 * levels, excluded operations, and the Summarised/Other skip. The output rendering is already byte-complete,
 * so asserting the built logs proves the capture-side parity.
 */
class SqlInteractionRecorderTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static SqlTrackingOptions.Builder opts() {
        return SqlTrackingOptions.builder()
            .serviceName("ShopDb")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1));
    }

    @Test
    void detailedRequestAndResponse() {
        SqlInteractionRecorder rec = new SqlInteractionRecorder(opts().build());

        Optional<SqlInteractionRecorder.Correlation> corr =
            rec.logRequest("SELECT * FROM Customers WHERE id = 1", "db-host", "shopdb", SqlCommandType.TEXT, null);
        assertThat(corr).isPresent();
        rec.logResponse(corr.get(), 3, null);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        // request: classified label as the method, raw SQL as content, table-qualified URI
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("SELECT FROM Customers");
        assertThat(req.content()).isEqualTo("SELECT * FROM Customers WHERE id = 1");
        assertThat(req.uri().toString()).isEqualTo("sql://db-host/shopdb/Customers");
        assertThat(req.serviceName()).isEqualTo("ShopDb");

        // response: empty method, row count, scheme-only URI, OK status, shared correlation
        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.method().value()).isEmpty();
        assertThat(res.content()).isEqualTo("3 rows affected");
        assertThat(res.uri().toString()).isEqualTo("sql:///");
        assertThat(res.statusCode()).isEqualTo(StatusCode.of("OK"));
        assertThat(req.traceId()).isEqualTo(res.traceId());
        assertThat(req.requestResponseId()).isEqualTo(res.requestResponseId());
    }

    @Test
    void rawVerbosityUsesKeywordMethodAndDataSourceUriAndParameters() {
        SqlInteractionRecorder rec = new SqlInteractionRecorder(opts().verbosity(TrackingVerbosity.RAW).build());

        rec.logRequest("SELECT * FROM Customers", "db-host", "shopdb", SqlCommandType.TEXT, "@id=1");

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("SELECT"); // raw keyword
        assertThat(req.uri().toString()).isEqualTo("sql://db-host/shopdb"); // no table in Raw
        assertThat(req.content()).isEqualTo("SELECT * FROM Customers\n-- Parameters: @id=1");
    }

    @Test
    void summarisedOmitsContentAndUsesSchemelessAuthorityUri() {
        SqlInteractionRecorder rec =
            new SqlInteractionRecorder(opts().verbosity(TrackingVerbosity.SUMMARISED).build());

        rec.logRequest("SELECT * FROM Customers", "db-host", "shopdb", SqlCommandType.TEXT, null);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("SELECT"); // summarised label
        assertThat(req.content()).isNull();
        assertThat(req.uri().toString()).isEqualTo("sql:///shopdb/Customers");
    }

    @Test
    void summarisedSkipsUnclassifiedOperations() {
        SqlInteractionRecorder rec =
            new SqlInteractionRecorder(opts().verbosity(TrackingVerbosity.SUMMARISED).build());

        Optional<SqlInteractionRecorder.Correlation> corr =
            rec.logRequest("VACUUM", "db-host", "shopdb", SqlCommandType.TEXT, null);

        assertThat(corr).isEmpty();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void excludedOperationsAreNotTracked() {
        SqlInteractionRecorder rec = new SqlInteractionRecorder(
            opts().excludedOperations(Set.of(UnifiedSqlOperation.SELECT)).build());

        assertThat(rec.logRequest("SELECT 1", "h", "db", SqlCommandType.TEXT, null)).isEmpty();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void responseExceptionMapsToErrorStatusAndMessage() {
        SqlInteractionRecorder rec = new SqlInteractionRecorder(opts().build());
        var corr = rec.logRequest("UPDATE t SET x=1", "h", "db", SqlCommandType.TEXT, null).orElseThrow();

        rec.logResponse(corr, (Integer) null, new IllegalStateException("constraint violation"));

        RequestResponseLog res = RequestResponseLogger.getAllLogs().get(1);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of("Error"));
        assertThat(res.content()).isEqualTo("constraint violation");
    }

    @Test
    void noTestContextEmitsNothing() {
        SqlInteractionRecorder rec = new SqlInteractionRecorder(opts().testInfoFetcher(() -> null).build());
        assertThat(rec.logRequest("SELECT 1", "h", "db", SqlCommandType.TEXT, null)).isEmpty();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
