package io.kronikol.report.flow;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe in-process store for captured {@link InternalFlowSpan}s. The OpenTelemetry capture bridge
 * (the {@code kronikol4j-opentelemetry} span processor) projects finished OTel spans into
 * {@link InternalFlowSpan}s and {@link #add}s them here; {@link InternalFlowSpanCollector} reads them at
 * report-generation time. Java port of the .NET {@code InternalFlowSpanStore} (which stores raw
 * {@code Activity} objects; the Java store keeps the runtime-neutral projection instead).
 *
 * <p>Deduplicates by span id (a span ends once), so multiple processors capturing the same span are safe.
 */
public final class InternalFlowSpanStore {

    private static final Queue<InternalFlowSpan> SPANS = new ConcurrentLinkedQueue<>();
    private static final Set<String> SEEN_SPAN_IDS = ConcurrentHashMap.newKeySet();

    private InternalFlowSpanStore() {
    }

    /** Adds a captured span (ignored if a span with the same non-null id was already added). */
    public static void add(InternalFlowSpan span) {
        if (span == null) {
            return;
        }
        if (span.spanId() == null || SEEN_SPAN_IDS.add(span.spanId())) {
            SPANS.add(span);
        }
    }

    /** All captured spans, in capture order (a snapshot). */
    public static List<InternalFlowSpan> getSpans() {
        return List.copyOf(SPANS);
    }

    /** The number of captured spans. */
    public static int count() {
        return SPANS.size();
    }

    /** Clears all captured spans (mandatory teardown between test runs). */
    public static void clear() {
        SEEN_SPAN_IDS.clear();
        SPANS.clear();
    }
}
