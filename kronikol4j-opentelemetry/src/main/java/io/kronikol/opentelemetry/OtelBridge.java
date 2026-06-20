package io.kronikol.opentelemetry;

import io.kronikol.core.tracking.RequestResponseLog;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Bridges OpenTelemetry into Kronikol4J (plan §3.8): reads the current span's trace/span ids and
 * stamps them onto a tracked log's distributed-tracing fields — the idiomatic mapping of the .NET
 * {@code Activity} span/trace ids. No-op when there is no valid current span.
 */
public final class OtelBridge {

    private OtelBridge() {
    }

    /** The current span's 32-hex trace id, or {@code null} if there is no valid current span. */
    public static String currentTraceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : null;
    }

    /** The current span's 16-hex span id, or {@code null} if there is no valid current span. */
    public static String currentSpanId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getSpanId() : null;
    }

    /** Stamps the current span's trace/span ids onto {@code log} (no-op if no valid current span). */
    public static void stamp(RequestResponseLog log) {
        SpanContext context = Span.current().getSpanContext();
        if (context.isValid()) {
            log.activityTraceId(context.getTraceId());
            log.activitySpanId(context.getSpanId());
        }
    }
}
