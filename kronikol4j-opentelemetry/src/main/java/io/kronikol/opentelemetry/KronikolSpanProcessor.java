package io.kronikol.opentelemetry;

import io.kronikol.report.flow.InternalFlowSpan;
import io.kronikol.report.flow.InternalFlowSpanStore;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Instant;

/**
 * An OpenTelemetry {@link SpanProcessor} that captures finished spans for InternalFlow diagrams — the Java
 * analog of the .NET {@code InternalFlowActivityListener}/{@code TestTrackingSpanExporter}. On span end it
 * projects the span into a runtime-neutral {@link InternalFlowSpan} and stores it in
 * {@link InternalFlowSpanStore}; {@code InternalFlowSpanCollector} reads from there at report time.
 *
 * <p>Register on the SDK tracer provider: {@code SdkTracerProvider.builder().addSpanProcessor(new
 * KronikolSpanProcessor())…}. End-only (no start hook). The .NET "exclude the AppInsights-conflict sources"
 * concern has no Java analog (there is no AppInsights DependencyTracking conflict), so all sources are
 * captured here and filtered later by {@code InternalFlowSpanCollector}'s granularity rules. The OTel SDK is
 * {@code compileOnly} — the user brings it.
 */
public final class KronikolSpanProcessor implements SpanProcessor {

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // no-op: capture happens on end
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        InternalFlowSpanStore.add(project(span.toSpanData()));
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    /** Projects an OTel {@link SpanData} into the runtime-neutral {@link InternalFlowSpan}. */
    static InternalFlowSpan project(SpanData data) {
        String parentSpanId = data.getParentSpanContext().isValid()
            ? data.getParentSpanContext().getSpanId() : null;
        String source = data.getInstrumentationScopeInfo().getName();
        Instant start = instantOf(data.getStartEpochNanos());
        double durationMs = (data.getEndEpochNanos() - data.getStartEpochNanos()) / 1_000_000.0;
        return new InternalFlowSpan(data.getSpanId(), parentSpanId, source, data.getName(), data.getName(),
            start, durationMs, data.getTraceId());
    }

    private static Instant instantOf(long epochNanos) {
        return Instant.ofEpochSecond(epochNanos / 1_000_000_000L, epochNanos % 1_000_000_000L);
    }
}
