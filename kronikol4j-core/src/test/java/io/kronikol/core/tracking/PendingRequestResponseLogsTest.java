package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.support.IdGenerator;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Ports .NET {@code PendingRequestResponseLogs} — the thread-safe queue that defers request/response log
 * entries until test identity is available, then flushes each as a request+response pair sharing one
 * trace id + request-response id. (.NET {@code Tracking/PendingRequestResponseLogs.cs}.)
 */
class PendingRequestResponseLogsTest {

    private static final OffsetDateTime TS = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @AfterEach
    void cleanup() {
        PendingRequestResponseLogs.clear();
        RequestResponseLogger.clear();
    }

    private static PendingLogEntry sampleEntry() {
        return PendingLogEntry.builder()
            .serviceName("Orders").callerName("Test")
            .method(Method.Http.POST)
            .requestContent("{\"a\":1}").responseContent("{\"ok\":true}")
            .uri(URI.create("http://orders/api"))
            .statusCode(StatusCode.of(201))
            .activityTraceId("trace-abc").activitySpanId("span-xyz")
            .dependencyCategory("Database")
            .timestamp(TS)
            .build();
    }

    @Test
    void enqueueIncrementsCount() {
        assertThat(PendingRequestResponseLogs.count()).isZero();
        PendingRequestResponseLogs.enqueue(sampleEntry());
        assertThat(PendingRequestResponseLogs.count()).isEqualTo(1);
    }

    @Test
    void flushAllEmitsRequestAndResponsePairThenDrains() {
        PendingRequestResponseLogs.enqueue(sampleEntry());

        PendingRequestResponseLogs.flushAll("MyTest", "id-1", IdGenerator.seeded(42));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        // shared correlation ids
        assertThat(req.traceId()).isEqualTo(res.traceId());
        assertThat(req.requestResponseId()).isEqualTo(res.requestResponseId());

        // request half
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.testName()).isEqualTo("MyTest");
        assertThat(req.testId()).isEqualTo("id-1");
        assertThat(req.method()).isEqualTo(Method.Http.POST);
        assertThat(req.content()).isEqualTo("{\"a\":1}");
        assertThat(req.uri()).isEqualTo(URI.create("http://orders/api"));
        assertThat(req.serviceName()).isEqualTo("Orders");
        assertThat(req.callerName()).isEqualTo("Test");
        assertThat(req.dependencyCategory()).isEqualTo("Database");
        assertThat(req.activityTraceId()).isEqualTo("trace-abc");
        assertThat(req.activitySpanId()).isEqualTo("span-xyz");
        assertThat(req.timestamp()).isEqualTo(TS);
        assertThat(req.statusCode()).isNull(); // request half has no status

        // response half
        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.content()).isEqualTo("{\"ok\":true}");
        assertThat(res.statusCode()).isEqualTo(StatusCode.of(201));
        assertThat(res.timestamp()).isEqualTo(TS);

        // queue drained
        assertThat(PendingRequestResponseLogs.count()).isZero();
    }

    @Test
    void flushAllOnEmptyQueueIsNoOp() {
        PendingRequestResponseLogs.flushAll("MyTest", "id-1", IdGenerator.seeded(1));
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void clearDiscardsPendingEntries() {
        PendingRequestResponseLogs.enqueue(sampleEntry());
        PendingRequestResponseLogs.clear();
        assertThat(PendingRequestResponseLogs.count()).isZero();
        PendingRequestResponseLogs.flushAll("MyTest", "id-1", IdGenerator.seeded(1));
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
