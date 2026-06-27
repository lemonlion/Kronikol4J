package io.kronikol.report.flow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Collects and filters captured {@link InternalFlowSpan}s by {@link InternalFlowSpanGranularity}. Java port of
 * the .NET {@code InternalFlowSpanCollector}. {@code AutoInstrumentation} keeps every span belonging to a
 * trace that contains at least one well-known auto-instrumentation span (so custom application spans on the
 * same trace are kept); {@code Manual} keeps only the named sources; {@code Full} keeps everything.
 */
public final class InternalFlowSpanCollector {

    /**
     * Well-known auto-instrumentation source (instrumentation-scope) names. The .NET set lists .NET libraries;
     * the Java equivalent is the OpenTelemetry-Java instrumentation namespace (any {@code io.opentelemetry.*}
     * scope) plus a few common explicit names and the Kronikol gRPC source.
     */
    public static final Set<String> WELL_KNOWN_AUTO_INSTRUMENTATION_SOURCES = Set.of(
        "io.opentelemetry.http",
        "io.opentelemetry.jdbc",
        "io.opentelemetry.okhttp-3.0",
        "io.opentelemetry.tomcat",
        "io.opentelemetry.netty",
        "io.opentelemetry.lettuce",
        "io.opentelemetry.mongo",
        "io.opentelemetry.kafka-clients",
        "io.opentelemetry.grpc-1.6",
        "Kronikol.Grpc",
        "kronikol");

    private InternalFlowSpanCollector() {
    }

    /** Collects spans from {@link InternalFlowSpanStore} and filters them by {@code granularity}. */
    public static List<InternalFlowSpan> collectSpans(InternalFlowSpanGranularity granularity,
                                                      List<String> manualActivitySources) {
        return filter(InternalFlowSpanStore.getSpans(), granularity, manualActivitySources);
    }

    /** Filters {@code spans} by {@code granularity} (exposed for testing without the global store). */
    static List<InternalFlowSpan> filter(List<InternalFlowSpan> spans, InternalFlowSpanGranularity granularity,
                                         List<String> manualActivitySources) {
        return switch (granularity) {
            case FULL -> spans;
            case MANUAL -> filterByManualSources(spans, manualActivitySources);
            case AUTO_INSTRUMENTATION -> filterByAutoInstrumentation(spans);
        };
    }

    private static List<InternalFlowSpan> filterByAutoInstrumentation(List<InternalFlowSpan> spans) {
        // Traces that contain at least one well-known auto-instrumentation span.
        Set<String> wellKnownTraceIds = new HashSet<>();
        for (InternalFlowSpan span : spans) {
            if (isAutoInstrumentationSource(span.sourceName()) && span.traceId() != null) {
                wellKnownTraceIds.add(span.traceId());
            }
        }
        List<InternalFlowSpan> result = new ArrayList<>();
        for (InternalFlowSpan span : spans) {
            if (span.traceId() != null && wellKnownTraceIds.contains(span.traceId())) {
                result.add(span);
            }
        }
        return result;
    }

    private static List<InternalFlowSpan> filterByManualSources(List<InternalFlowSpan> spans,
                                                                List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return spans;
        }
        Set<String> sourceSet = new HashSet<>();
        for (String s : sources) {
            sourceSet.add(s.toLowerCase(Locale.ROOT));
        }
        List<InternalFlowSpan> result = new ArrayList<>();
        for (InternalFlowSpan span : spans) {
            if (span.sourceName() != null && sourceSet.contains(span.sourceName().toLowerCase(Locale.ROOT))) {
                result.add(span);
            }
        }
        return result;
    }

    /** Whether a source name is a known auto-instrumentation scope (explicit set or {@code io.opentelemetry.*}). */
    static boolean isAutoInstrumentationSource(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) {
            return false;
        }
        return WELL_KNOWN_AUTO_INSTRUMENTATION_SOURCES.contains(sourceName)
            || sourceName.startsWith("io.opentelemetry.");
    }
}
