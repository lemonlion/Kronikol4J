package io.kronikol.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link TrackingProxy} emits an OpenTelemetry span per tracked call when
 * {@code activitySourceName} is set (the .NET {@code ActivitySource} — feeds the InternalFlow span capture),
 * and stays silent when it is not. Uses a real in-memory OTel SDK as the global instance.
 */
class TrackingProxyOtelSpanTest {

    interface Calculator {
        int add(int a, int b);
    }

    @AfterEach
    void cleanup() {
        GlobalOpenTelemetry.resetForTest();
        RequestResponseLogger.clear();
    }

    private static ProxyOptions options() {
        return ProxyOptions.forService("Calc")
            .withTestInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .withIds(IdGenerator.seeded(1));
    }

    @Test
    void emitsOneSpanPerCallFromTheNamedTracer() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        OpenTelemetrySdk.builder().setTracerProvider(provider).buildAndRegisterGlobal();
        try {
            Calculator calc = TrackingProxy.wrap(Calculator.class, Integer::sum,
                options().withActivitySourceName("kronikol.proxy"));

            assertThat(calc.add(2, 3)).isEqualTo(5); // the real call still runs and returns

            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSize(1);
            assertThat(spans.get(0).getName()).isEqualTo("Calculator.add");
            assertThat(spans.get(0).getInstrumentationScopeInfo().getName()).isEqualTo("kronikol.proxy");
        } finally {
            provider.close();
        }
    }

    @Test
    void emitsNoSpanWhenActivitySourceNameUnset() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        OpenTelemetrySdk.builder().setTracerProvider(provider).buildAndRegisterGlobal();
        try {
            Calculator calc = TrackingProxy.wrap(Calculator.class, Integer::sum, options());
            assertThat(calc.add(2, 3)).isEqualTo(5);
            assertThat(exporter.getFinishedSpanItems()).isEmpty(); // no activitySourceName → no span
        } finally {
            provider.close();
        }
    }
}
