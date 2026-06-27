package io.kronikol.opentelemetry;

import io.kronikol.core.tracking.TrackingTraceContext;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds an OpenTelemetry parent {@link SpanContext} from the ambient {@link TrackingTraceContext} trace id —
 * the OTel half of the .NET {@code TrackingTraceContext.CreateParentContext} (kept out of zero-dependency
 * core). The proxy's span production uses this as the parent of the spans it emits, tying them to the
 * Kronikol trace. Complements the read-only {@link OtelBridge}.
 */
public final class OtelTraceContext {

    private OtelTraceContext() {
    }

    /**
     * A remote, sampled parent {@link SpanContext} whose trace id is derived from the current
     * {@link TrackingTraceContext} trace id (with a fresh random span id), or {@link SpanContext#getInvalid()}
     * when no trace scope is active (the .NET {@code default(ActivityContext)}).
     */
    public static SpanContext createParentContext() {
        UUID traceId = TrackingTraceContext.currentTraceId();
        if (traceId == null) {
            return SpanContext.getInvalid();
        }
        String traceIdHex = String.format("%016x%016x",
            traceId.getMostSignificantBits(), traceId.getLeastSignificantBits());
        return SpanContext.createFromRemoteParent(
            traceIdHex, randomSpanIdHex(), TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static String randomSpanIdHex() {
        long id = ThreadLocalRandom.current().nextLong();
        if (id == 0L) {
            id = 1L; // a zero span id is invalid
        }
        return String.format("%016x", id);
    }
}
