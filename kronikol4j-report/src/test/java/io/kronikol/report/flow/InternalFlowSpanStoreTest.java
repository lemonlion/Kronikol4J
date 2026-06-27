package io.kronikol.report.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies the {@link InternalFlowSpanStore} (capture-side span store): add, span-id dedup, clear. */
class InternalFlowSpanStoreTest {

    @BeforeEach
    @AfterEach
    void reset() {
        InternalFlowSpanStore.clear();
    }

    private static InternalFlowSpan span(String spanId, String trace) {
        return new InternalFlowSpan(spanId, null, "io.opentelemetry.http", "GET", "GET",
            Instant.EPOCH, 5.0, trace);
    }

    @Test
    void addsAndReturnsSpansInOrder() {
        InternalFlowSpanStore.add(span("s1", "t"));
        InternalFlowSpanStore.add(span("s2", "t"));
        assertThat(InternalFlowSpanStore.getSpans()).extracting(InternalFlowSpan::spanId)
            .containsExactly("s1", "s2");
        assertThat(InternalFlowSpanStore.count()).isEqualTo(2);
    }

    @Test
    void deduplicatesBySpanId() {
        InternalFlowSpanStore.add(span("s1", "t"));
        InternalFlowSpanStore.add(span("s1", "t")); // same id → ignored
        assertThat(InternalFlowSpanStore.getSpans()).hasSize(1);
    }

    @Test
    void clearEmptiesTheStore() {
        InternalFlowSpanStore.add(span("s1", "t"));
        InternalFlowSpanStore.clear();
        assertThat(InternalFlowSpanStore.getSpans()).isEmpty();
        // a previously-seen id can be added again after clear
        InternalFlowSpanStore.add(span("s1", "t"));
        assertThat(InternalFlowSpanStore.getSpans()).hasSize(1);
    }

    @Test
    void nullSpanIgnored() {
        InternalFlowSpanStore.add(null);
        assertThat(InternalFlowSpanStore.getSpans()).isEmpty();
    }

    @Test
    void activitySourceDiscoveryReturnsDistinctSortedSources() {
        InternalFlowSpanStore.add(new InternalFlowSpan("s1", null, "io.opentelemetry.jdbc", "q", "q",
            Instant.EPOCH, 1.0, "t"));
        InternalFlowSpanStore.add(new InternalFlowSpan("s2", null, "io.opentelemetry.http", "g", "g",
            Instant.EPOCH, 1.0, "t"));
        InternalFlowSpanStore.add(new InternalFlowSpan("s3", null, "io.opentelemetry.http", "g", "g",
            Instant.EPOCH, 1.0, "t"));
        assertThat(ActivitySourceDiscovery.discoveredSources())
            .containsExactly("io.opentelemetry.http", "io.opentelemetry.jdbc"); // distinct + sorted
    }
}
