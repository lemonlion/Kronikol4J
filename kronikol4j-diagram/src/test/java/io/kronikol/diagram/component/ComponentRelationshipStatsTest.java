package io.kronikol.diagram.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies {@link ComponentRelationshipStats#compute} — request/response pairing by id + timestamp,
 *  duration percentiles (the .NET interpolation), error rate, distinct-test count and low-coverage flag. */
class ComponentRelationshipStatsTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 6, 28, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void computesPercentilesErrorRateAndCoverage() {
        // Three calls Test→OrderDb across three tests: 10ms, 20ms, 30ms; the 30ms one errors (500).
        List<RequestResponseLog> logs = new ArrayList<>();
        logs.addAll(call("t1", 10, 200));
        logs.addAll(call("t2", 20, 200));
        logs.addAll(call("t3", 30, 500));

        var relationships = ComponentDiagramGenerator.extractRelationships(logs);
        Map<String, ComponentRelationshipStats> stats =
            ComponentRelationshipStats.compute(relationships, logs, 3);

        var s = stats.get(ComponentRelationshipStats.relationshipKey("Test", "OrderDb"));
        assertThat(s).isNotNull();
        assertThat(s.callCount()).isEqualTo(3);
        assertThat(s.testCount()).isEqualTo(3);
        assertThat(s.minMs()).isEqualTo(10.0);
        assertThat(s.maxMs()).isEqualTo(30.0);
        assertThat(s.medianMs()).isEqualTo(20.0);                 // P50 of [10,20,30]
        assertThat(s.p95Ms()).isCloseTo(29.0, offset(0.0001));    // 20 + 0.9*(30-20)
        assertThat(s.errorRate()).isCloseTo(1.0 / 3.0, offset(1e-9));
        assertThat(s.isLowCoverage()).isFalse();                  // 3 tests, threshold 3 → not below
    }

    @Test
    void lowCoverageWhenFewerTestsThanThreshold() {
        List<RequestResponseLog> logs = new ArrayList<>(call("t1", 15, 200));
        var rels = ComponentDiagramGenerator.extractRelationships(logs);

        var s = ComponentRelationshipStats.compute(rels, logs, 3)
            .get(ComponentRelationshipStats.relationshipKey("Test", "OrderDb"));

        assertThat(s.testCount()).isEqualTo(1);
        assertThat(s.isLowCoverage()).isTrue(); // 1 < 3
    }

    @Test
    void unpairedRequestsAreIgnored() {
        // A request with no matching response contributes no duration → no stats entry.
        RequestResponseLog lonelyRequest = RequestResponseLog.builder()
            .testName("t1").testId("t1").method(Method.of("SELECT")).uri(URI.create("sql://db/"))
            .serviceName("OrderDb").callerName("Test").type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .dependencyCategory(DependencyCategories.SQL).timestamp(T0).build();
        var logs = List.of(lonelyRequest);

        var rels = ComponentDiagramGenerator.extractRelationships(logs);
        assertThat(ComponentRelationshipStats.compute(rels, logs, 3)).isEmpty();
    }

    /** A paired request+response for the Test→OrderDb relationship lasting {@code durationMs} with {@code status}. */
    private static List<RequestResponseLog> call(String testId, long durationMs, int status) {
        UUID rr = UUID.randomUUID();
        UUID trace = UUID.randomUUID();
        RequestResponseLog request = RequestResponseLog.builder()
            .testName(testId).testId(testId).method(Method.of("SELECT")).uri(URI.create("sql://db/"))
            .serviceName("OrderDb").callerName("Test").type(RequestResponseType.REQUEST)
            .traceId(trace).requestResponseId(rr).dependencyCategory(DependencyCategories.SQL)
            .timestamp(T0).build();
        RequestResponseLog response = RequestResponseLog.builder()
            .testName(testId).testId(testId).method(Method.of("SELECT")).uri(URI.create("sql://db/"))
            .serviceName("OrderDb").callerName("Test").type(RequestResponseType.RESPONSE)
            .traceId(trace).requestResponseId(rr).dependencyCategory(DependencyCategories.SQL)
            .statusCode(StatusCode.of(status)).timestamp(T0.plusNanos(durationMs * 1_000_000)).build();
        return List.of(request, response);
    }
}
