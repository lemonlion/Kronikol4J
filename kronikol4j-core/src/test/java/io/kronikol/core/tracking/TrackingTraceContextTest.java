package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the {@link TrackingTraceContext} ambient trace-id scope (the .NET {@code TrackingTraceContext}
 *  analog) — push/restore nesting and consistent {@code currentTraceId}. */
class TrackingTraceContextTest {

    @Test
    void noTraceActiveByDefault() {
        assertThat(TrackingTraceContext.currentTraceId()).isNull();
    }

    @Test
    void beginTraceSetsCurrentAndExposesTheId() {
        try (TrackingTraceContext.TraceScope scope = TrackingTraceContext.beginTrace()) {
            UUID id = scope.traceId();
            assertThat(id).isNotNull();
            assertThat(TrackingTraceContext.currentTraceId()).isEqualTo(id);
        }
        assertThat(TrackingTraceContext.currentTraceId()).isNull(); // cleared on close
    }

    @Test
    void nestedTracesRestoreThePreviousIdOnClose() {
        try (TrackingTraceContext.TraceScope outer = TrackingTraceContext.beginTrace()) {
            UUID outerId = outer.traceId();
            try (TrackingTraceContext.TraceScope inner = TrackingTraceContext.beginTrace()) {
                assertThat(TrackingTraceContext.currentTraceId()).isEqualTo(inner.traceId());
                assertThat(inner.traceId()).isNotEqualTo(outerId);
            }
            assertThat(TrackingTraceContext.currentTraceId()).isEqualTo(outerId); // restored
        }
        assertThat(TrackingTraceContext.currentTraceId()).isNull();
    }
}
