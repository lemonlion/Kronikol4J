package io.kronikol.diagram.component;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-relationship performance statistics consumed by the component diagram — the component-diagram-relevant
 * core of the .NET {@code RelationshipStats} / {@code ComponentFlowSegmentBuilder.ComputeRelationshipStats}:
 * call/test counts, the latency percentiles ({@link #medianMs}/{@link #p95Ms}/{@link #p99Ms}, plus mean/min/max),
 * the error rate, and the low-coverage flag. Keyed per {@code (caller → service)} relationship.
 *
 * <p>The fuller .NET {@code RelationshipStats} additionally carries endpoint breakdowns, payload sizes,
 * concurrency, outliers, status/method distributions and graph metrics — those feed the standalone stats
 * report, not the component-diagram arrow, and are a separate (larger) port. This type is exactly what
 * {@link ComponentDiagramGenerator#generatePlantUml(List, ComponentDiagramRenderOptions, Map)} needs.
 *
 * @param callCount  paired request/response calls
 * @param testCount  distinct tests that exercised the relationship
 * @param meanMs     mean call duration (ms)
 * @param medianMs   P50 duration (ms)
 * @param p95Ms      P95 duration (ms)
 * @param p99Ms      P99 duration (ms)
 * @param minMs      fastest call (ms)
 * @param maxMs      slowest call (ms)
 * @param errorRate  fraction of calls with an HTTP status &ge; 400 (0..1)
 * @param isLowCoverage  {@code testCount < lowCoverageThreshold}
 */
public record ComponentRelationshipStats(
    int callCount, int testCount, double meanMs, double medianMs, double p95Ms, double p99Ms,
    double minMs, double maxMs, double errorRate, boolean isLowCoverage) {

    /** The stats key for a relationship — {@code iflow-rel-<caller>-<service>} (the .NET relKey). */
    public static String relationshipKey(String caller, String service) {
        return "iflow-rel-" + sanitizeKey(caller) + "-" + sanitizeKey(service);
    }

    /** The .NET {@code SanitizeKey}: spaces / slashes / backslashes → underscore. */
    public static String sanitizeKey(String name) {
        return name.replace(' ', '_').replace('/', '_').replace('\\', '_');
    }

    /**
     * Computes per-relationship stats from the tracked logs, keyed by {@link #relationshipKey}. Pairs each
     * request with its response by {@code requestResponseId} (both timestamped), measures the duration, and
     * aggregates per {@code (caller, service)}. Skips tracking-ignored logs and override/action-start markers.
     * Mirrors {@code ComponentFlowSegmentBuilder.ComputeRelationshipStats} for the consumed fields.
     */
    public static Map<String, ComponentRelationshipStats> compute(
            List<ComponentRelationship> relationships, List<RequestResponseLog> logs, int lowCoverageThreshold) {
        Map<String, ComponentRelationshipStats> result = new LinkedHashMap<>();
        if (relationships.isEmpty() || logs.isEmpty()) {
            return result;
        }

        Map<Object, RequestResponseLog> responseLookup = new HashMap<>();
        List<RequestResponseLog> requestLogs = new ArrayList<>();
        for (RequestResponseLog log : logs) {
            if (log.timestamp() == null) {
                continue;
            }
            if (log.type() == RequestResponseType.RESPONSE) {
                responseLookup.putIfAbsent(log.requestResponseId(), log);
            } else if (log.type() == RequestResponseType.REQUEST
                && !log.trackingIgnore() && !log.overrideStart() && !log.overrideEnd() && !log.actionStart()) {
                requestLogs.add(log);
            }
        }

        for (ComponentRelationship rel : relationships) {
            List<Double> durations = new ArrayList<>();
            Set<String> tests = new HashSet<>();
            int errorCount = 0;
            for (RequestResponseLog req : requestLogs) {
                if (!req.callerName().equals(rel.caller()) || !req.serviceName().equals(rel.service())) {
                    continue;
                }
                RequestResponseLog res = responseLookup.get(req.requestResponseId());
                if (res == null) {
                    continue;
                }
                double durationMs = millisBetween(req.timestamp(), res.timestamp());
                if (durationMs < 0) {
                    continue;
                }
                durations.add(durationMs);
                tests.add(req.testId());
                if (isError(res.statusCode())) {
                    errorCount++;
                }
            }
            if (durations.isEmpty()) {
                continue;
            }
            durations.sort(Double::compareTo);
            double[] sorted = durations.stream().mapToDouble(Double::doubleValue).toArray();
            int callCount = sorted.length;
            int testCount = tests.size();
            result.put(relationshipKey(rel.caller(), rel.service()), new ComponentRelationshipStats(
                callCount, testCount, mean(sorted), percentile(sorted, 50), percentile(sorted, 95),
                percentile(sorted, 99), sorted[0], sorted[callCount - 1],
                (double) errorCount / callCount, testCount < lowCoverageThreshold));
        }
        return result;
    }

    private static double millisBetween(OffsetDateTime request, OffsetDateTime response) {
        return Duration.between(request, response).toNanos() / 1_000_000.0;
    }

    private static boolean isError(StatusCode status) {
        return status instanceof StatusCode.Http http && http.code() >= 400;
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** The .NET linear-interpolation percentile over an ascending-sorted array. */
    static double percentile(double[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        double index = (percentile / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted[lower];
        }
        double fraction = index - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
}
