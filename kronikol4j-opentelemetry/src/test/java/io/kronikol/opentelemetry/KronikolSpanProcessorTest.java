package io.kronikol.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.flow.InternalFlowSpan;
import io.kronikol.report.flow.InternalFlowSpanStore;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies {@link KronikolSpanProcessor} captures finished OTel spans into {@link InternalFlowSpanStore},
 *  projected to the runtime-neutral {@link InternalFlowSpan}. */
class KronikolSpanProcessorTest {

    @BeforeEach
    @AfterEach
    void reset() {
        InternalFlowSpanStore.clear();
    }

    @Test
    void capturesFinishedSpansWithProjectedFields() {
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(new KronikolSpanProcessor())
                .build()) {
            Tracer tracer = provider.get("my.instrumentation.scope");
            Span parent = tracer.spanBuilder("parent-op").startSpan();
            Span child = tracer.spanBuilder("child-op").setParent(
                io.opentelemetry.context.Context.current().with(parent)).startSpan();
            String parentId = parent.getSpanContext().getSpanId();
            String childId = child.getSpanContext().getSpanId();
            String traceId = parent.getSpanContext().getTraceId();
            child.end();
            parent.end();

            var spans = InternalFlowSpanStore.getSpans();
            assertThat(spans).extracting(InternalFlowSpan::spanId).containsExactlyInAnyOrder(parentId, childId);

            InternalFlowSpan parentSpan = spans.stream().filter(s -> s.spanId().equals(parentId))
                .findFirst().orElseThrow();
            assertThat(parentSpan.sourceName()).isEqualTo("my.instrumentation.scope");
            assertThat(parentSpan.operationName()).isEqualTo("parent-op");
            assertThat(parentSpan.traceId()).isEqualTo(traceId);
            assertThat(parentSpan.parentSpanId()).isNull(); // root

            InternalFlowSpan childSpan = spans.stream().filter(s -> s.spanId().equals(childId))
                .findFirst().orElseThrow();
            assertThat(childSpan.parentSpanId()).isEqualTo(parentId); // linked to parent
            assertThat(childSpan.durationMs()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    void isEndOnly() {
        KronikolSpanProcessor processor = new KronikolSpanProcessor();
        assertThat(processor.isStartRequired()).isFalse();
        assertThat(processor.isEndRequired()).isTrue();
    }
}
