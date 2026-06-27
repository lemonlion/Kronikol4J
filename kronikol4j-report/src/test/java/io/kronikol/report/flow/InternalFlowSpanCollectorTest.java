package io.kronikol.report.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the {@link InternalFlowSpanCollector} granularity filtering (the .NET collector's rules). */
class InternalFlowSpanCollectorTest {

    private static InternalFlowSpan span(String spanId, String source, String trace) {
        return new InternalFlowSpan(spanId, null, source, "op", "op", Instant.EPOCH, 1.0, trace);
    }

    // trace A has a well-known auto-instrumentation span + an app span; trace B has only an app span.
    private static final List<InternalFlowSpan> SPANS = List.of(
        span("a1", "io.opentelemetry.http", "A"),
        span("a2", "MyApp.Service", "A"),
        span("b1", "MyApp.Service", "B"));

    @Test
    void fullKeepsEverything() {
        assertThat(InternalFlowSpanCollector.filter(SPANS, InternalFlowSpanGranularity.FULL, null))
            .hasSize(3);
    }

    @Test
    void autoInstrumentationKeepsWholeTracesContainingAWellKnownSpan() {
        List<InternalFlowSpan> kept = InternalFlowSpanCollector.filter(
            SPANS, InternalFlowSpanGranularity.AUTO_INSTRUMENTATION, null);
        // both trace-A spans kept (incl. the custom app span); trace-B span dropped
        assertThat(kept).extracting(InternalFlowSpan::spanId).containsExactly("a1", "a2");
    }

    @Test
    void manualKeepsOnlyNamedSourcesCaseInsensitively() {
        List<InternalFlowSpan> kept = InternalFlowSpanCollector.filter(
            SPANS, InternalFlowSpanGranularity.MANUAL, List.of("myapp.service"));
        assertThat(kept).extracting(InternalFlowSpan::spanId).containsExactly("a2", "b1");
    }

    @Test
    void manualWithNoSourcesKeepsEverything() {
        assertThat(InternalFlowSpanCollector.filter(SPANS, InternalFlowSpanGranularity.MANUAL, List.of()))
            .hasSize(3);
    }

    @Test
    void recognisesWellKnownAndOtelPrefixedSources() {
        assertThat(InternalFlowSpanCollector.isAutoInstrumentationSource("io.opentelemetry.jdbc")).isTrue();
        assertThat(InternalFlowSpanCollector.isAutoInstrumentationSource("io.opentelemetry.anything")).isTrue();
        assertThat(InternalFlowSpanCollector.isAutoInstrumentationSource("Kronikol.Grpc")).isTrue();
        assertThat(InternalFlowSpanCollector.isAutoInstrumentationSource("MyApp.Service")).isFalse();
        assertThat(InternalFlowSpanCollector.isAutoInstrumentationSource("")).isFalse();
    }
}
