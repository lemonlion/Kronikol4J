package io.kronikol.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

/**
 * Auto-registers the {@link KronikolSpanProcessor} on the OpenTelemetry SDK with zero wiring — the Java analog
 * of the .NET InternalFlow {@code ActivityListener} DI/eager-start registration. Discovered via the OTel
 * autoconfigure SPI ({@code META-INF/services}), so it applies whenever the SDK is built through
 * {@code AutoConfiguredOpenTelemetrySdk} — the OTel Java agent, the Spring Boot OTel starter, or a manual
 * {@code AutoConfiguredOpenTelemetrySdk.builder()...build()}. The autoconfigure SPI is {@code compileOnly}.
 *
 * <p>Opt out with {@code -Dotel.kronikol.internalflow.enabled=false} (or the {@code OTEL_KRONIKOL_INTERNALFLOW_ENABLED}
 * env var); enabled by default.
 */
public final class KronikolAutoConfigurationCustomizerProvider implements AutoConfigurationCustomizerProvider {

    @Override
    public void customize(AutoConfigurationCustomizer customizer) {
        customizer.addTracerProviderCustomizer((builder, config) -> {
            if (config.getBoolean("otel.kronikol.internalflow.enabled", true)) {
                builder.addSpanProcessor(new KronikolSpanProcessor());
            }
            return builder;
        });
    }
}
