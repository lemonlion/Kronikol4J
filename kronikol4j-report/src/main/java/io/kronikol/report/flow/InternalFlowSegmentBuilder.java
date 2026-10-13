package io.kronikol.report.flow;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Builds the whole-test internal-flow segments from captured spans (a port of the .NET
 * {@code InternalFlowSegmentBuilder.BuildWholeTestSegments}). One segment per test, keyed
 * {@code iflow-test-{testId}}, holding the test's spans ordered by start time.
 *
 * <p>This is the data-production side that feeds {@link WholeTestFlowInput}: the OpenTelemetry capture
 * (a {@code SpanProcessor}/exporter collecting finished spans) is the runtime-integration boundary the
 * caller supplies — convert each finished span to an {@link InternalFlowSpan} (carrying its trace id),
 * then call {@link #buildWholeTestSegments} to correlate spans to tests by trace id.
 */
public final class InternalFlowSegmentBuilder {

    /** A test's records in time order; a stable sort, so records that tie keep the order they were captured in. */
    private static final Comparator<RequestResponseLog> TIME_ORDER =
        Comparator.comparing(RequestResponseLog::timestamp);

    private InternalFlowSegmentBuilder() {
    }

    /**
     * Groups spans into per-test segments. Spans are correlated to a test by trace id (via the test's
     * {@link RequestResponseLog#activityTraceId()}); a test whose logs carry no trace id keeps all
     * spans (mirroring .NET). Tests with no matching spans are excluded. Preserves first-seen test order.
     */
    public static Map<String, InternalFlowSegment> buildWholeTestSegments(
            List<RequestResponseLog> logs, List<InternalFlowSpan> spans) {
        Map<String, InternalFlowSegment> segments = new LinkedHashMap<>();
        if (spans.isEmpty() || logs.isEmpty()) {
            return segments;
        }
        Map<String, List<RequestResponseLog>> logsByTest = new LinkedHashMap<>();
        for (RequestResponseLog l : logs) {
            if (l.timestamp() != null) {
                logsByTest.computeIfAbsent(l.testId(), k -> new ArrayList<>()).add(l);
            }
        }
        for (Map.Entry<String, List<RequestResponseLog>> e : logsByTest.entrySet()) {
            List<InternalFlowSpan> testSpans = filterSpansByTestTraceIds(spans, e.getValue());
            if (testSpans.isEmpty()) {
                continue;
            }
            List<InternalFlowSpan> ordered = new ArrayList<>(testSpans);
            ordered.sort(Comparator.comparing(InternalFlowSpan::startTime));
            segments.put("iflow-test-" + e.getKey(), new InternalFlowSegment(e.getKey(), ordered));
        }
        return segments;
    }

    /**
     * Builds the per-diagram (per HTTP boundary) segments for the interactive popup (.NET
     * {@code BuildSegments}): one segment per request log, keyed {@code iflow-{requestResponseId}},
     * spanning from the request timestamp to its matching response (else the next log, else +5s), with
     * spans in {@code [start-50ms, end)} correlated by trace id. Sets {@code requestResponseId} +
     * {@code boundaryType} on each segment.
     *
     * <p>Calls of one test that share a trace under span ids of their own (one trace per test, as a browser
     * test sends or a proxy tap forwards) are told apart by the span tree too (.NET #170): a span that
     * descends from another of those calls' span ids is that call's, unless that call ran inside this one,
     * as {@code kronikol query flow} nests calls. A span that descends from none of them stays in every call
     * whose time holds it. A trace that calls of several tests record keeps every span in each call's window
     * (.NET's #87 rule for it is not mirrored).
     */
    public static Map<String, InternalFlowSegment> buildSegments(
            List<RequestResponseLog> logs, List<InternalFlowSpan> spans) {
        Map<String, InternalFlowSegment> segments = new LinkedHashMap<>();
        if (spans.isEmpty()) {
            return segments;
        }
        Map<String, List<RequestResponseLog>> logsByTest = new LinkedHashMap<>();
        for (RequestResponseLog l : logs) {
            if (l.timestamp() != null) {
                logsByTest.computeIfAbsent(l.testId(), k -> new ArrayList<>()).add(l);
            }
        }
        OneTestCalls oneTestCalls = new OneTestCalls(logsByTest, spans);
        for (Map.Entry<String, List<RequestResponseLog>> e : logsByTest.entrySet()) {
            List<RequestResponseLog> ordered = new ArrayList<>(e.getValue());
            ordered.sort(TIME_ORDER);
            List<InternalFlowSpan> testSpans = filterSpansByTestTraceIds(spans, ordered);
            Map<UUID, OffsetDateTime> responseTs = new HashMap<>();
            for (RequestResponseLog l : ordered) {
                if (l.type() == RequestResponseType.RESPONSE && l.timestamp() != null) {
                    responseTs.putIfAbsent(l.requestResponseId(), l.timestamp());
                }
            }
            for (int i = 0; i < ordered.size(); i++) {
                RequestResponseLog log = ordered.get(i);
                if (log.type() != RequestResponseType.REQUEST) {
                    continue;
                }
                OffsetDateTime segmentStart = log.timestamp();
                OffsetDateTime resp = responseTs.get(log.requestResponseId());
                OffsetDateTime segmentEnd = resp != null ? resp
                    : i + 1 < ordered.size() ? ordered.get(i + 1).timestamp() : segmentStart.plusSeconds(5);
                String traceId = log.activityTraceId();
                Instant toleranceStart = segmentStart.toInstant().minusMillis(50);
                Instant end = segmentEnd.toInstant();
                Predicate<InternalFlowSpan> ownSpan = oneTestCalls.ownSpans(log);
                List<InternalFlowSpan> segmentSpans = new ArrayList<>();
                for (InternalFlowSpan s : testSpans) {
                    if (traceId != null && !traceId.equals(s.traceId())) {
                        continue;
                    }
                    if (!s.startTime().isBefore(toleranceStart) && s.startTime().isBefore(end) && ownSpan.test(s)) {
                        segmentSpans.add(s);
                    }
                }
                segmentSpans.sort(Comparator.comparing(InternalFlowSpan::startTime));
                segments.put("iflow-" + log.requestResponseId(), new InternalFlowSegment(
                    log.testId(), segmentSpans, log.requestResponseId().toString(),
                    log.type().name().toLowerCase(Locale.ROOT)));
            }
        }
        return segments;
    }

    private static List<InternalFlowSpan> filterSpansByTestTraceIds(
            List<InternalFlowSpan> allSpans, List<RequestResponseLog> testLogs) {
        Set<String> traceIds = new HashSet<>();
        for (RequestResponseLog l : testLogs) {
            if (l.activityTraceId() != null) {
                traceIds.add(l.activityTraceId());
            }
        }
        if (traceIds.isEmpty()) {
            return allSpans;
        }
        List<InternalFlowSpan> filtered = new ArrayList<>();
        for (InternalFlowSpan s : allSpans) {
            if (s.traceId() != null && traceIds.contains(s.traceId())) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    /**
     * The span ids each test's calls record in each trace, and which calls record each, which tell one test's
     * calls apart in a trace they share (.NET #170): a span whose chain names the span id of another of those
     * calls is that call's, unless that call ran inside this one, whose work it then is. Worked out once per
     * {@link #buildSegments} call.
     */
    private static final class OneTestCalls {

        /** The Kronikol trace id of a record that has none ({@code Guid.Empty} in .NET). */
        private static final UUID NO_TRACE = new UUID(0L, 0L);

        private final List<InternalFlowSpan> spans;
        /** Per trace id, the tests whose calls record it. */
        private final Map<String, Set<String>> testsByTrace = new HashMap<>();
        /** Per trace and test, each span id the test's calls record in the trace, with the calls that record it. */
        private final Map<TraceAndTest, Map<String, List<RequestResponseLog>>> claimers = new HashMap<>();
        /** Per call (a request record, told apart by identity), the call it ran inside. */
        private final Map<RequestResponseLog, RequestResponseLog> outerOf = new IdentityHashMap<>();
        /** Per trace id, its spans by span id (the first span of each id), built when first needed. */
        private final Map<String, Map<String, InternalFlowSpan>> spanById = new HashMap<>();
        /** Per span, told apart by identity as .NET tells spans apart, the claimed span ids on its chain. */
        private final Map<InternalFlowSpan, Set<String>> claimedAbove = new IdentityHashMap<>();

        private record TraceAndTest(String trace, String test) {
        }

        /** {@code logsByTest}: each test's timed records, in the order they were captured. */
        OneTestCalls(Map<String, List<RequestResponseLog>> logsByTest, List<InternalFlowSpan> spans) {
            this.spans = spans;
            for (List<RequestResponseLog> testLogs : logsByTest.values()) {
                for (RequestResponseLog l : testLogs) {
                    String trace = l.activityTraceId();
                    if (!isCall(l) || trace == null) {
                        continue;
                    }
                    testsByTrace.computeIfAbsent(trace, k -> new HashSet<>()).add(l.testId());
                    // A call is its request: only a request claims the span id it records.
                    if (l.type() == RequestResponseType.REQUEST && l.activitySpanId() != null) {
                        claimers.computeIfAbsent(new TraceAndTest(trace, l.testId()), k -> new HashMap<>())
                            .computeIfAbsent(l.activitySpanId(), k -> new ArrayList<>()).add(l);
                    }
                }
                nest(testLogs);
            }
        }

        /** As in .NET: a diagram marker or a user action is not a call. */
        private static boolean isCall(RequestResponseLog l) {
            return !l.diagramMarker() && !l.userAction();
        }

        /**
         * Which of a test's calls ran inside which, by the rule {@code kronikol query flow} nests calls by (.NET
         * {@code CallNesting}, rule R4): made while the other was still waiting for its answer, by the service
         * handling it, or by another party on its Kronikol trace. Two calls from one party are never nested.
         */
        private void nest(List<RequestResponseLog> testLogs) {
            // In time order, as the segments are cut; records that tie keep the order they were captured in.
            List<RequestResponseLog> entries = new ArrayList<>(testLogs);
            entries.sort(TIME_ORDER);
            // A request is open only while its answer is still ahead of it.
            Map<UUID, Integer> answeredAt = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).type() == RequestResponseType.RESPONSE) {
                    answeredAt.putIfAbsent(entries.get(i).requestResponseId(), i);
                }
            }
            List<RequestResponseLog> open = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                RequestResponseLog entry = entries.get(i);
                if (entry.type() == RequestResponseType.RESPONSE) {
                    open.removeIf(o -> o.requestResponseId().equals(entry.requestResponseId()));
                    continue;
                }
                if (!isCall(entry)) {
                    continue;
                }
                for (int k = open.size() - 1; k >= 0; k--) {
                    if (madeInside(entry, open.get(k))) {
                        outerOf.put(entry, open.get(k));
                        break;
                    }
                }
                Integer answer = answeredAt.get(entry.requestResponseId());
                if (answer != null && answer > i) {
                    open.add(entry);
                }
            }
        }

        /**
         * Whether {@code call}, made while {@code open} was waiting for its answer, was made inside it: by the
         * service handling it, or by another party on its Kronikol trace.
         */
        private static boolean madeInside(RequestResponseLog call, RequestResponseLog open) {
            return open.serviceName().equals(call.callerName())
                || (!open.callerName().equals(call.callerName())
                    && !NO_TRACE.equals(call.traceId()) && open.traceId().equals(call.traceId()));
        }

        /** Whether {@code call} is {@code outer} or ran inside it, following the outer links up. */
        private boolean isOrRanInside(RequestResponseLog call, RequestResponseLog outer) {
            // Each link leads to a call opened earlier, so the walk ends; the bound only guards a record handed in
            // twice, which could otherwise become its own outer call.
            RequestResponseLog at = call;
            for (int hops = 0; at != null && hops <= outerOf.size(); hops++) {
                if (at == outer) {
                    return true;
                }
                at = outerOf.get(at);
            }
            return false;
        }

        /**
         * Which spans of its trace {@code call} (a request) may take by the tree. Every span, unless the trace's
         * only test records it under two or more span ids: then a span whose chain names none of them, and a
         * span whose chain names one recorded by this call or by a call that ran inside it.
         */
        Predicate<InternalFlowSpan> ownSpans(RequestResponseLog call) {
            String trace = call.activityTraceId();
            if (trace == null || call.activitySpanId() == null
                    || testsByTrace.getOrDefault(trace, Set.of()).size() > 1) {
                return s -> true;
            }
            Map<String, List<RequestResponseLog>> claimed = claimers.get(new TraceAndTest(trace, call.testId()));
            if (claimed == null || claimed.size() < 2) {
                return s -> true;
            }
            return s -> {
                Set<String> above = claimedAbove(trace, s, claimed.keySet());
                if (above.isEmpty()) {
                    return true;
                }
                for (String id : above) {
                    for (RequestResponseLog by : claimed.get(id)) {
                        if (isOrRanInside(by, call)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }

        /**
         * The claimed span ids on the span's chain: its own, its parent's, and up through every parent the
         * store holds. A parent the store does not hold (the span a tap forwarded, recorded in another
         * process) is checked too; the walk stops there.
         */
        private Set<String> claimedAbove(String trace, InternalFlowSpan span, Set<String> claimed) {
            Set<String> known = claimedAbove.get(span);
            if (known != null) {
                return known;
            }
            Map<String, InternalFlowSpan> byId = spanById.computeIfAbsent(trace, this::spansById);
            Set<String> found = new HashSet<>();
            Set<String> visited = new HashSet<>();
            String id = span.spanId();
            while (id != null && visited.add(id)) {
                if (claimed.contains(id)) {
                    found.add(id);
                }
                InternalFlowSpan held = byId.get(id);
                id = held != null ? held.parentSpanId() : null;
            }
            claimedAbove.put(span, found);
            return found;
        }

        private Map<String, InternalFlowSpan> spansById(String trace) {
            Map<String, InternalFlowSpan> byId = new HashMap<>();
            for (InternalFlowSpan s : spans) {
                if (trace.equals(s.traceId()) && s.spanId() != null) {
                    byId.putIfAbsent(s.spanId(), s);
                }
            }
            return byId;
        }
    }
}
