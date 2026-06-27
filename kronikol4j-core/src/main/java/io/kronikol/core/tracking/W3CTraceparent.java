package io.kronikol.core.tracking;

import io.kronikol.core.support.IdGenerator;
import java.util.UUID;

/**
 * A W3C Trace Context {@code traceparent} value (version 00): {@code 00-<32-hex trace-id>-<16-hex span-id>-00}.
 * Outbound trackers inject this so a downstream tracked service joins the same trace. Mirrors the .NET
 * handler's {@code "00-{traceId}-{spanId}-00"} injection.
 *
 * @param traceId 32 lower-case hex chars (128-bit), never all-zero
 * @param spanId  16 lower-case hex chars (64-bit), never all-zero
 */
public record W3CTraceparent(String traceId, String spanId) {

    /** The {@code traceparent} header value. */
    public String header() {
        return "00-" + traceId + "-" + spanId + "-00";
    }

    /**
     * Generates a fresh traceparent from the determinism seam ({@link IdGenerator}). The trace-id is a
     * full UUID's 128 bits; the span-id is the high 64 bits of a second id.
     */
    public static W3CTraceparent generate(IdGenerator ids) {
        UUID t = ids.newId();
        UUID s = ids.newId();
        String traceId = String.format("%016x%016x", t.getMostSignificantBits(), t.getLeastSignificantBits());
        String spanId = String.format("%016x", s.getMostSignificantBits());
        return new W3CTraceparent(traceId, spanId);
    }
}
