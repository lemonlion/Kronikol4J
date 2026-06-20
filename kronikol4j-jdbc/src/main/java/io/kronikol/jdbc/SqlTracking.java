package io.kronikol.jdbc;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TestPhase;
import java.net.URI;
import java.util.UUID;

/**
 * Records a SQL execution as a tracked interaction (request = the statement, response = a result
 * summary). The reusable core that a {@code TrackingDataSource}/Hibernate {@code StatementInspector}
 * delegates to. Direct {@code log()} construction ingestion pattern (plan §1/§3.4); one JDBC-level
 * tracker covers every relational database (plan §2).
 */
public final class SqlTracking {

    private static final URI SQL_URI = URI.create("sql://database/");

    private SqlTracking() {
    }

    /**
     * @param resultSummary a short description of the outcome (e.g. {@code "3 rows"}); may be {@code null}.
     */
    public static void record(JdbcTrackingOptions options, String sql, String resultSummary) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }

        UUID trace = UUID.randomUUID();
        UUID rr = UUID.randomUUID();
        TestPhase phase = TestPhaseContext.current();
        Method verb = Method.of(SqlOperationClassifier.keyword(sql));

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(verb).uri(SQL_URI)
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(trace).requestResponseId(rr)
            .dependencyCategory(options.dependencyCategory()).content(sql)
            .phase(phase).build());

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(verb).uri(SQL_URI)
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE).traceId(trace).requestResponseId(rr)
            .statusCode(StatusCode.of("OK")).dependencyCategory(options.dependencyCategory())
            .content(resultSummary)
            .phase(phase).build());
    }
}
