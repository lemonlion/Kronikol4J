package io.kronikol.proxy;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Bridges the {@link TrackingProxy} to OpenTelemetry: starts a span per tracked call from the named tracker
 * (the .NET {@code ActivitySource}), so the {@code KronikolSpanProcessor} (kronikol4j-opentelemetry) captures
 * it into the InternalFlow span store. opentelemetry-api is {@code compileOnly}; all access is isolated here
 * and guarded so a classpath without OTel (or with no SDK configured) silently no-ops.
 */
final class ProxyOtelSpan {

    private ProxyOtelSpan() {
    }

    /**
     * Starts a span named {@code spanName} on the {@code tracerName} tracer and makes it current; returns an
     * {@link AutoCloseable} that ends the span (and restores the previous context), or {@code null} when OTel
     * is unavailable. Never throws.
     */
    static AutoCloseable start(String tracerName, String spanName) {
        try {
            Span span = GlobalOpenTelemetry.getTracer(tracerName).spanBuilder(spanName).startSpan();
            Scope scope = span.makeCurrent();
            return () -> {
                try {
                    scope.close();
                } finally {
                    span.end();
                }
            };
        } catch (Throwable otelUnavailable) {
            return null; // opentelemetry-api not on the classpath, or no SDK — no span
        }
    }
}
