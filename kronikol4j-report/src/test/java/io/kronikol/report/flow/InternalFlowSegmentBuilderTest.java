package io.kronikol.report.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    void buildSegments_callOfTheTestThatRecordsNoSpanId_keepsEverySpanOfTheTraceInItsTime() {
        // A tracker that records the trace but no span gives the tree nothing to tell its call by, so the call
        // keeps every span of the trace in its time; the test's calls that record span ids are still told apart.
        List<RequestResponseLog> a = call("t1", 0, 100, "traceA", "a");
        List<RequestResponseLog> b = call("t1", 10, 110, "traceA", "b");
        List<RequestResponseLog> c = call("t1", 20, 120, "traceA", null);

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(a, b, c), List.of(
            span("a-server", 30, "traceA", "a"),
            span("b-server", 35, "traceA", "b")));

        assertEquals(List.of("a-server", "b-server"), names(segs.get(key(c))), "c keeps every span of the trace in its time");
        assertEquals(List.of("a-server"), names(segs.get(key(a))), "a keeps only its own span");
        assertEquals(List.of("b-server"), names(segs.get(key(b))), "b keeps only its own span");
    }

    @Test
    void buildSegments_traceOfSeveralTests_callWithNothingUnderItsSpanKeepsItsTestsOtherCallsSpans() {
        // A trace that calls of several tests record is not split by one test's span ids. .NET gives such a
        // trace #87's rule: aFirst, whose span id has nothing under it, takes every span of the trace in its
        // time, and b-server, which test-b's call takes too, goes to neither. The port does not mirror #87 and
        // keeps every span. Either way aFirst keeps a-second-server, which the rule for one test's calls would
        // give to aSecond alone. Presence only, so the port's pre-#87 output is not pinned.
        List<RequestResponseLog> aFirst = call("test-a", 0, 100, "traceA", "a1");
        List<RequestResponseLog> aSecond = call("test-a", 10, 110, "traceA", "a2");
        List<RequestResponseLog> b = call("test-b", 0, 100, "traceA", "b");

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(
            logsOf(aFirst, aSecond, b), List.of(
                span("a-second-server", 20, "traceA", "a2"),
                span("b-server", 25, "traceA", "b")));

        assertTrue(names(segs.get(key(aFirst))).contains("a-second-server"),
            "aFirst keeps its test's other call's span: one test's span ids do not split a trace of several tests");
    }

    @Test
    void buildSegments_callKeepsTheSpansOfTheCallsMadeInsideIt_whateverSpanTheyRecorded() {
        // The shape of a Jest and OpenTelemetry capture: the test's call is recorded under the span of the
        // service's request hook, and the calls the service makes while handling it under its resolver's span,
        // a sibling of the hook's. Those calls ran inside the test's call (made by the service handling it,
        // while it waited), so their spans are its work too; each inner call keeps out the hook's.
        List<RequestResponseLog> outer = call("t1", 0, 100, "traceA", "hook", "Payments", "Test", null);
        List<RequestResponseLog> psp = call("t1", 20, 50, "traceA", "resolver", "PSP", "Payments", null);
        List<RequestResponseLog> risk = call("t1", 55, 80, "traceA", "resolver", "Risk", "Payments", null);

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(outer, psp, risk), List.of(
            span("server", 5, "traceA", null),
            span("hook", 6, "traceA", "server"),
            span("resolver", 10, "traceA", "server"),
            span("psp-post", 25, "traceA", "resolver"),
            span("risk-get", 60, "traceA", "resolver")));

        assertEquals(List.of("server", "hook", "resolver", "psp-post", "risk-get"), names(segs.get(key(outer))),
            "the test's call keeps the spans of the calls its service made inside it");
        assertEquals(List.of("server", "resolver", "psp-post"), names(segs.get(key(psp))),
            "an inner call keeps out the hook's span");
        assertFalse(names(segs.get(key(risk))).contains("hook"), "an inner call keeps out the hook's span");
        assertTrue(names(segs.get(key(risk))).contains("risk-get"), "an inner call keeps its own spans");
    }

    @Test
    void buildSegments_callKeepsTheSpansOfADeliveryOnItsKronikolTrace() {
        // A message published inside the test's call is delivered broker to consumer: the broker is not the
        // service the test called, but the delivery carries the call's Kronikol trace id, which is how kronikol
        // query flow nests it inside the call. Its handler's spans are the call's work too.
        UUID kronikolTrace = UUID.randomUUID();
        List<RequestResponseLog> order = call("t1", 0, 100, "traceA", "order-span", "Orders", "Test", kronikolTrace);
        List<RequestResponseLog> delivery =
            call("t1", 30, 60, "traceA", "delivery-span", "Worker", "Broker", kronikolTrace);

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(order, delivery), List.of(
            span("orders-server", 10, "traceA", "order-span"),
            span("worker-handler", 40, "traceA", "delivery-span")));

        assertEquals(List.of("orders-server", "worker-handler"), names(segs.get(key(order))),
            "the call keeps the spans of the delivery on its Kronikol trace");
        assertEquals(List.of("worker-handler"), names(segs.get(key(delivery))),
            "the delivery keeps out the call's own span");
    }

    @Test
    void buildSegments_callMadeAfterAnotherWasAnswered_didNotRunInsideIt() {
        // A request is open only until its answer. The service's call made after the test's call was answered
        // did not run inside it, so the spans under the span that later call recorded stay out of the test's call.
        List<RequestResponseLog> first = call("t1", 0, 50, "traceA", "call", "Api", "Test", null);
        List<RequestResponseLog> later = call("t1", 70, 90, "traceA", "job", "Db", "Api", null);

        Map<String, InternalFlowSegment> segs = InternalFlowSegmentBuilder.buildSegments(logsOf(first, later), List.of(
            span("api-server", 5, "traceA", "call"),
            span("job", 35, "traceA", null),
            span("job-step", 40, "traceA", "job")));

        assertEquals(List.of("api-server"), names(segs.get(key(first))),
            "the call keeps out the spans of a call made after it was answered");
    }

    @Test
    void buildSegments_callRecordHandedInTwice_doesNotHangTheBuilder() {
        // A record handed in twice is one call and never runs inside itself, and the walk up the calls a call ran
        // inside always ends. a's service is its own caller, so its second record would otherwise sit inside its first.
        List<RequestResponseLog> a = call("t1", 0, 100, "traceA", "a", "Orders", "Orders", null);
        List<RequestResponseLog> b = call("t1", 10, 110, "traceA", "b");
        List<InternalFlowSpan> spans = List.of(span("a-server", 20, "traceA", "a"), span("b-server", 25, "traceA", "b"));

        // Preemptively: a walk that never ends does not answer an interrupt.
        Map<String, InternalFlowSegment> segs = assertTimeoutPreemptively(Duration.ofSeconds(30),
            () -> InternalFlowSegmentBuilder.buildSegments(logsOf(a, b, List.of(a.get(0))), spans));

        assertEquals(List.of("a-server"), names(segs.get(key(a))), "a keeps its own span");
        assertEquals(List.of("b-server"), names(segs.get(key(b))), "b keeps its own span");
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
        return call(testId, fromMs, toMs, traceId, spanId, "Svc", "Test", null);
    }

    /**
     * As {@link #call(String, int, int, String, String)}, made by {@code caller} to {@code service}. Each record
     * carries {@code kronikolTrace} as its Kronikol trace id, or a fresh one of its own when that is null.
     */
    private static List<RequestResponseLog> call(String testId, int fromMs, int toMs, String traceId, String spanId,
                                                 String service, String caller, UUID kronikolTrace) {
        UUID id = UUID.randomUUID();
        return List.of(
            boundary(testId, traceId, RequestResponseType.REQUEST, id, T0.plusMillis(fromMs), service, caller,
                kronikolTrace != null ? kronikolTrace : UUID.randomUUID()).activitySpanId(spanId),
            boundary(testId, traceId, RequestResponseType.RESPONSE, id, T0.plusMillis(toMs), service, caller,
                kronikolTrace != null ? kronikolTrace : UUID.randomUUID()).activitySpanId(spanId));
    }

    @SafeVarargs
    private static List<RequestResponseLog> logsOf(List<RequestResponseLog>... calls) {
        List<RequestResponseLog> logs = new ArrayList<>();
        for (List<RequestResponseLog> call : calls) {
            logs.addAll(call);
        }
        return logs;
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
        return boundary(testId, traceId, type, rrid, ts, "Svc", "Test", UUID.randomUUID());
    }

    private static RequestResponseLog boundary(String testId, String traceId, RequestResponseType type,
                                               UUID rrid, Instant ts, String service, String caller,
                                               UUID kronikolTrace) {
        RequestResponseLog l = RequestResponseLog.builder()
            .testName(testId).testId(testId)
            .method(Method.Http.GET).uri(URI.create("http://svc/x"))
            .serviceName(service).callerName(caller)
            .type(type).traceId(kronikolTrace).requestResponseId(rrid)
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
