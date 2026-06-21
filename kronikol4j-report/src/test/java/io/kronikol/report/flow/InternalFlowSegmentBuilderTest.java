package io.kronikol.report.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InternalFlowSegmentBuilder} (a port of .NET BuildWholeTestSegments). */
class InternalFlowSegmentBuilderTest {

    private static final Instant T0 = Instant.parse("2024-01-15T10:00:00Z");

    @Test
    void correlatesSpansToTestsByTraceId_orderedByStartTime() {
        RequestResponseLog l1 = log("t1", "traceA");
        RequestResponseLog l2 = log("t2", "traceB");
        // t1 spans (out of order) + a t2 span + an unrelated span (traceC) that belongs to no test.
        List<InternalFlowSpan> spans = List.of(
            span("a2", "a1", "GET /a child", T0.plusMillis(20), 5, "traceA"),
            span("a1", null, "GET /a", T0, 30, "traceA"),
            span("b1", null, "GET /b", T0.plusMillis(5), 10, "traceB"),
            span("x1", null, "orphan", T0, 1, "traceC"));

        Map<String, InternalFlowSegment> segs =
            InternalFlowSegmentBuilder.buildWholeTestSegments(List.of(l1, l2), spans);

        assertEquals(2, segs.size());
        InternalFlowSegment s1 = segs.get("iflow-test-t1");
        assertEquals(List.of("a1", "a2"), s1.spans().stream().map(InternalFlowSpan::spanId).toList(),
            "t1 spans correlated by traceA and ordered by start time");
        InternalFlowSegment s2 = segs.get("iflow-test-t2");
        assertEquals(List.of("b1"), s2.spans().stream().map(InternalFlowSpan::spanId).toList());
        assertTrue(!segs.containsKey("iflow-test-x1"), "unrelated trace produces no segment");
    }

    @Test
    void testWithoutTraceId_keepsAllSpans() {
        RequestResponseLog l = log("t1", null); // no activityTraceId → all spans
        List<InternalFlowSpan> spans = List.of(
            span("a", null, "A", T0, 5, "traceA"),
            span("b", null, "B", T0.plusMillis(1), 5, "traceB"));

        Map<String, InternalFlowSegment> segs =
            InternalFlowSegmentBuilder.buildWholeTestSegments(List.of(l), spans);

        assertEquals(2, segs.get("iflow-test-t1").spans().size());
    }

    @Test
    void buildSegments_perBoundary_windowsSpansAndKeysByRequestId() {
        UUID rr = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RequestResponseLog req = boundary("t1", "traceA", RequestResponseType.REQUEST, rr, T0);
        RequestResponseLog resp = boundary("t1", "traceA", RequestResponseType.RESPONSE, rr, T0.plusMillis(100));
        List<InternalFlowSpan> spans = List.of(
            span("a", null, "in window", T0.plusMillis(10), 5, "traceA"),
            span("b", null, "after end", T0.plusMillis(200), 5, "traceA"));

        Map<String, InternalFlowSegment> segs =
            InternalFlowSegmentBuilder.buildSegments(List.of(req, resp), spans);

        assertEquals(1, segs.size());
        InternalFlowSegment s = segs.get("iflow-" + rr);
        assertEquals(List.of("a"), s.spans().stream().map(InternalFlowSpan::spanId).toList(),
            "only spans in [start-50ms, response) are included");
        assertEquals(rr.toString(), s.requestResponseId());
        assertEquals("request", s.boundaryType());
    }

    @Test
    void emptyInputs_yieldNoSegments() {
        assertTrue(InternalFlowSegmentBuilder.buildWholeTestSegments(List.of(), List.of()).isEmpty());
        assertTrue(InternalFlowSegmentBuilder.buildWholeTestSegments(
            List.of(log("t1", "traceA")), List.of()).isEmpty());
    }

    private static InternalFlowSpan span(String id, String parent, String name, Instant start,
                                         double durMs, String traceId) {
        return new InternalFlowSpan(id, parent, "Svc", null, name, start, durMs, traceId);
    }

    private static RequestResponseLog boundary(String testId, String traceId, RequestResponseType type,
                                               UUID rrid, Instant ts) {
        RequestResponseLog l = RequestResponseLog.builder()
            .testName(testId).testId(testId)
            .method(Method.Http.GET).uri(URI.create("http://svc/x"))
            .serviceName("Svc").callerName("Test")
            .type(type).traceId(UUID.randomUUID()).requestResponseId(rrid)
            .build();
        l.timestamp(ts.atOffset(java.time.ZoneOffset.UTC));
        l.activityTraceId(traceId);
        return l;
    }

    private static RequestResponseLog log(String testId, String traceId) {
        RequestResponseLog l = RequestResponseLog.builder()
            .testName(testId).testId(testId)
            .method(Method.Http.GET).uri(URI.create("http://svc/x"))
            .serviceName("Svc").callerName("Test")
            .type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .build();
        l.timestamp(OffsetDateTime.parse("2024-01-15T10:00:00Z"));
        l.activityTraceId(traceId);
        return l;
    }
}
