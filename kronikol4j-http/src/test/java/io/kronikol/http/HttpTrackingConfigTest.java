package io.kronikol.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link HttpTrackingConfig} options surface — the Java analog of the .NET
 * {@code TestTrackingMessageHandlerOptions} — including the defaults and the three fields completed for
 * Tier-2 parity ({@code headersToForward}, {@code currentStepTypeFetcher}, {@code internalFlowActivitySources}).
 */
class HttpTrackingConfigTest {

    @Test
    void defaultsMatchDotNet() {
        HttpTrackingConfig c = HttpTrackingConfig.defaults();
        assertThat(c.fixedServiceName()).isNull();
        assertThat(c.clientName()).isNull();
        assertThat(c.clientNamesToServiceNames()).isEmpty();
        assertThat(c.portsToServiceNames()).isEmpty();
        assertThat(c.callerName()).isEqualTo(TrackingDefaults.CALLER_NAME);
        assertThat(c.dependencyCategory()).isEqualTo(DependencyCategories.HTTP);
        assertThat(c.testInfoFetcher()).isNull();
        assertThat(c.trackDuringSetup()).isTrue();
        assertThat(c.trackDuringAction()).isTrue();
        assertThat(c.injectTraceparent()).isTrue();
        assertThat(c.verbosity()).isEqualTo(TrackingVerbosity.DETAILED);
        assertThat(c.ids()).isNotNull();
        // The three Tier-2 fields.
        assertThat(c.headersToForward()).isEmpty();
        assertThat(c.currentStepTypeFetcher()).isNull();
        assertThat(c.internalFlowActivitySources()).isEmpty();
    }

    @Test
    void builderSetsTheTierTwoFields() {
        HttpTrackingConfig c = HttpTrackingConfig.builder()
            .headersToForward(List.of("X-Correlation-Id", "X-Tenant"))
            .currentStepTypeFetcher(() -> "Given")
            .internalFlowActivitySources(List.of("MyApp.Tracing", "MyApp.Db"))
            .build();

        assertThat(c.headersToForward()).containsExactly("X-Correlation-Id", "X-Tenant");
        assertThat(c.currentStepTypeFetcher().get()).isEqualTo("Given");
        assertThat(c.internalFlowActivitySources()).containsExactly("MyApp.Tracing", "MyApp.Db");
    }

    @Test
    void nullCollectionsCoalesceToEmptyAndAreDefensivelyCopied() {
        HttpTrackingConfig c = HttpTrackingConfig.builder()
            .headersToForward(null)
            .internalFlowActivitySources(null)
            .build();
        assertThat(c.headersToForward()).isEmpty();
        assertThat(c.internalFlowActivitySources()).isEmpty();

        java.util.List<String> mutable = new java.util.ArrayList<>(List.of("a"));
        HttpTrackingConfig copied = HttpTrackingConfig.builder().headersToForward(mutable).build();
        mutable.add("b");
        assertThat(copied.headersToForward()).containsExactly("a"); // snapshot, not a live view
    }
}
