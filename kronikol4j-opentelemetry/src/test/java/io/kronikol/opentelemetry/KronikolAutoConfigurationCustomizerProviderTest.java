package io.kronikol.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.flow.InternalFlowSpanStore;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link KronikolAutoConfigurationCustomizerProvider} is discovered via the OTel autoconfigure
 * SPI and registers the {@link KronikolSpanProcessor} on the SDK with zero wiring — so a span ended through an
 * autoconfigured SDK lands in the {@link InternalFlowSpanStore}. Honours the opt-out flag.
 */
class KronikolAutoConfigurationCustomizerProviderTest {

    @AfterEach
    void cleanup() {
        InternalFlowSpanStore.clear();
    }

    private static OpenTelemetrySdk autoconfiguredSdk(Map<String, String> extraProps) {
        Map<String, String> props = new java.util.HashMap<>(Map.of(
            "otel.traces.exporter", "none", "otel.metrics.exporter", "none", "otel.logs.exporter", "none"));
        props.putAll(extraProps);
        // build() does not register globally by default (global is opt-in via the no-arg setResultAsGlobal()).
        return AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> props)
            .build()
            .getOpenTelemetrySdk();
    }

    @Test
    void spanProcessorIsAutoRegisteredSoSpansLandInTheStore() {
        InternalFlowSpanStore.clear();
        OpenTelemetrySdk sdk = autoconfiguredSdk(Map.of());
        try {
            Span span = sdk.getTracer("test.scope").spanBuilder("readOrders").startSpan();
            span.end();

            assertThat(InternalFlowSpanStore.getSpans()).anySatisfy(s -> {
                assertThat(s.displayName()).isEqualTo("readOrders");
                assertThat(s.sourceName()).isEqualTo("test.scope");
            });
        } finally {
            sdk.getSdkTracerProvider().close();
        }
    }

    @Test
    void optOutFlagDisablesAutoRegistration() {
        InternalFlowSpanStore.clear();
        OpenTelemetrySdk sdk = autoconfiguredSdk(Map.of("otel.kronikol.internalflow.enabled", "false"));
        try {
            sdk.getTracer("test.scope").spanBuilder("readOrders").startSpan().end();
            assertThat(InternalFlowSpanStore.count()).isZero(); // processor not registered
        } finally {
            sdk.getSdkTracerProvider().close();
        }
    }
}
