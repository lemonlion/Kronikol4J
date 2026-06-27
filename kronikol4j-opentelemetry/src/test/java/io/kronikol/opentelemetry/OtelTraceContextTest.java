package io.kronikol.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingTraceContext;
import io.opentelemetry.api.trace.SpanContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies {@link OtelTraceContext#createParentContext} builds a valid sampled remote parent span context
 *  from the ambient {@link TrackingTraceContext} trace id (the .NET {@code CreateParentContext} analog). */
class OtelTraceContextTest {

    @Test
    void returnsInvalidWhenNoTraceActive() {
        assertThat(OtelTraceContext.createParentContext().isValid()).isFalse();
    }

    @Test
    void buildsSampledRemoteParentFromCurrentTraceId() {
        try (TrackingTraceContext.TraceScope scope = TrackingTraceContext.beginTrace()) {
            UUID id = scope.traceId();
            SpanContext ctx = OtelTraceContext.createParentContext();

            assertThat(ctx.isValid()).isTrue();
            assertThat(ctx.isRemote()).isTrue();
            assertThat(ctx.isSampled()).isTrue();
            // trace id derives from the UUID's two longs
            String expectedTraceId = String.format("%016x%016x",
                id.getMostSignificantBits(), id.getLeastSignificantBits());
            assertThat(ctx.getTraceId()).isEqualTo(expectedTraceId);
            assertThat(ctx.getSpanId()).matches("[0-9a-f]{16}").isNotEqualTo("0000000000000000");
        }
    }
}
