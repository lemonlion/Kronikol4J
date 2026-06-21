package io.kronikol.report.flow;

import io.kronikol.core.tracking.RequestResponseLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
}
