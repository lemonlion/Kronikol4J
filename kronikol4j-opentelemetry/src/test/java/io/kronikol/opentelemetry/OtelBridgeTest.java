package io.kronikol.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OtelBridgeTest {

    private static RequestResponseLog sampleLog() {
        return RequestResponseLog.builder()
            .testName("T").testId("t").method(Method.Http.GET).uri(URI.create("http://s/x"))
            .serviceName("S").callerName("Test").type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .build();
    }

    @Test
    void stampsCurrentSpanIdsOntoLog() {
        try (SdkTracerProvider provider = SdkTracerProvider.builder().build()) {
            Tracer tracer = provider.get("test");
            Span span = tracer.spanBuilder("op").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                String traceId = span.getSpanContext().getTraceId();
                String spanId = span.getSpanContext().getSpanId();

                assertThat(OtelBridge.currentTraceId()).isEqualTo(traceId);
                assertThat(OtelBridge.currentSpanId()).isEqualTo(spanId);

                RequestResponseLog log = sampleLog();
                OtelBridge.stamp(log);
                assertThat(log.activityTraceId()).isEqualTo(traceId);
                assertThat(log.activitySpanId()).isEqualTo(spanId);
            } finally {
                span.end();
            }
        }
    }

    @Test
    void noCurrentSpanLeavesIdsNull() {
        assertThat(OtelBridge.currentTraceId()).isNull();
        RequestResponseLog log = sampleLog();
        OtelBridge.stamp(log);
        assertThat(log.activityTraceId()).isNull();
    }
}
