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
import java.util.stream.Stream;
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

    // ─── One test's calls in one trace (.NET #170, F4) ───
    // .NET's facts also check that no segment counts the other call's spans as left out; the port has no
    // SpansLeftOut (.NET #87 is not mirrored), so that check has no Java form.

    @Test
    void buildSegments_overlappingCallsOfOneTestInOneTrace_keepTheirOwnSpans() {
        // One trace per test, as a browser test sends or a tap minting the test's trace forwards: two calls
        // that overlap in time, each forwarded under its own span id, which the store does not hold (the
        // tap's client span is in another process). The span tree gives each server span to one call.
        List<RequestResponseLog> a = call("t1", 0, 100, "traceA", "a-call");
        List<RequestResponseLog> b = call("t1", 10, 110, "traceA", "b-call");

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(a, b), List.of(
            span("a-server", 20, "traceA", "a-call"),
            span("b-server", 25, "traceA", "b-call"),
            span("a-query", 30, "traceA", "a-server"),
            span("b-query", 35, "traceA", "b-server")));

        assertEquals(List.of("a-server", "a-query"), names(segs.get(key(a))), "a's popup holds only a's spans");
        assertEquals(List.of("b-server", "b-query"), names(segs.get(key(b))), "b's popup holds only b's spans");
    }

    @Test
    void buildSegments_nestedCallOfOneTest_stillShowsInsideItsOuterCall() {
        // The outer call's service calls onward inside it, and the nested call is forwarded under the span the
        // service started for that: its spans descend from both calls' span ids, so both popups show them. A
        // span of the outer call's own work is the outer call's, though it starts inside the nested call's time.
        List<RequestResponseLog> outer = call("t1", 0, 200, "traceA", "outer-call");
        List<RequestResponseLog> nested = call("t1", 40, 150, "traceA", "outer-client");

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(outer, nested), List.of(
            span("outer-server", 10, "traceA", "outer-call"),
            span("outer-client", 40, "traceA", "outer-server"),
            span("nested-server", 50, "traceA", "outer-client"),
            span("nested-query", 60, "traceA", "nested-server"),
            span("outer-side", 70, "traceA", "outer-server")));

        assertEquals(List.of("outer-server", "outer-client", "nested-server", "nested-query", "outer-side"),
            names(segs.get(key(outer))), "the outer popup holds its own spans and the nested call's");
        assertEquals(List.of("outer-client", "nested-server", "nested-query"), names(segs.get(key(nested))),
            "the nested popup holds the nested call's spans, not the outer call's own work");
    }

    @Test
    void buildSegments_spansThatDescendFromNoCall_stayInEveryOverlappingWindow() {
        // A span whose chain names neither call's span id (in process, its parent was a span no listener kept)
        // cannot be given to either call by the tree, so it stays where its time puts it, as before.
        List<RequestResponseLog> a = call("t1", 0, 100, "traceA", "a-call");
        List<RequestResponseLog> b = call("t1", 10, 110, "traceA", "b-call");

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(a, b), List.of(
            span("a-server", 20, "traceA", "a-call"),
            span("b-server", 25, "traceA", "b-call"),
            span("unlinked", 30, "traceA", "unkept-parent")));

        assertEquals(List.of("a-server", "unlinked"), names(segs.get(key(a))), "a keeps its span and the unlinked one");
        assertEquals(List.of("b-server", "unlinked"), names(segs.get(key(b))), "b keeps its span and the unlinked one");
    }

    @Test
    void buildSegments_oneTestsCallsUnderOneSpanId_keepTodaysSelection() {
        // In process, every call of a test may record the one ambient span it runs under: nothing tells the
        // calls apart by tree, so each keeps every span of the trace in its time.
        List<RequestResponseLog> a = call("t1", 0, 100, "traceA", "ambient");
        List<RequestResponseLog> b = call("t1", 10, 110, "traceA", "ambient");

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(a, b), List.of(
            span("under", 20, "traceA", "ambient"),
            span("beside", 30, "traceA", "unkept-parent")));

        assertEquals(List.of("under", "beside"), names(segs.get(key(a))), "a keeps every span of the trace in its time");
        assertEquals(List.of("under", "beside"), names(segs.get(key(b))), "b keeps every span of the trace in its time");
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

    /** A 5 ms span of {@code traceId} starting {@code startMs} after T0 under {@code parentSpanId}; its id is its name. */
    private static InternalFlowSpan span(String name, int startMs, String traceId, String parentSpanId) {
        return span(name, parentSpanId, name, T0.plusMillis(startMs), 5, traceId);
    }

    /** A request and its response, {@code fromMs} to {@code toMs} after T0, both recording the trace and span id. */
    private static List<RequestResponseLog> call(String testId, int fromMs, int toMs, String traceId, String spanId) {
        UUID id = UUID.randomUUID();
        return List.of(
            boundary(testId, traceId, RequestResponseType.REQUEST, id, T0.plusMillis(fromMs)).activitySpanId(spanId),
            boundary(testId, traceId, RequestResponseType.RESPONSE, id, T0.plusMillis(toMs)).activitySpanId(spanId));
    }

    private static List<RequestResponseLog> logsOf(List<RequestResponseLog> first, List<RequestResponseLog> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    /** The popup segment's key for a call made by {@link #call}. */
    private static String key(List<RequestResponseLog> call) {
        return "iflow-" + call.get(0).requestResponseId();
    }

    private static List<String> names(InternalFlowSegment segment) {
        return segment.spans().stream().map(InternalFlowSpan::operationName).toList();
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
