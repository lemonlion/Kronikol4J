package io.kronikol.core.tracking;

import java.util.UUID;

/**
 * Ambient ({@link ThreadLocal}-backed) trace context that correlates request/response pairs produced by
 * different tracking components within the same logical operation. Java port of the .NET
 * {@code TrackingTraceContext} — the <em>write</em> counterpart to the read-only OTel bridge: it mints a new
 * ambient trace id that the proxy's span production (and a parent span context) can build on.
 *
 * <p>{@link #beginTrace()} pushes a fresh trace id and returns an {@link AutoCloseable} {@link TraceScope}
 * (use try-with-resources — clearing is mandatory, §3.2) that restores the previous id on close, so traces
 * nest. Build an OpenTelemetry parent {@code SpanContext} from the current id with
 * {@code kronikol4j-opentelemetry}'s {@code OtelTraceContext.createParentContext()} (kept there so core stays
 * zero-dependency).
 */
public final class TrackingTraceContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TrackingTraceContext() {
    }

    /** The current ambient trace id, or {@code null} if no trace scope is active. */
    public static UUID currentTraceId() {
        return CURRENT.get();
    }

    /** Pushes a fresh ambient trace id; returns a scope that restores the previous id on {@link #close()}. */
    public static TraceScope beginTrace() {
        UUID previous = CURRENT.get();
        UUID traceId = UUID.randomUUID();
        CURRENT.set(traceId);
        return new TraceScope(traceId, previous);
    }

    /** An active trace scope. Closing it restores the previously-active trace id (supports nesting). */
    public static final class TraceScope implements AutoCloseable {
        private final UUID traceId;
        private final UUID previous;

        private TraceScope(UUID traceId, UUID previous) {
            this.traceId = traceId;
            this.previous = previous;
        }

        /** The trace id this scope established. */
        public UUID traceId() {
            return traceId;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
