package io.kronikol.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.serialization.TrackingSerializerOptions;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link MessageTrackerOptions} surface — the Java analog of the .NET {@code MessageTrackerOptions}
 * — including the three fields completed for Tier-2 parity ({@code currentStepTypeFetcher},
 * {@code useHttpContextCorrelation}, {@code serializerOptions}).
 */
class MessageTrackerOptionsTest {

    @Test
    void defaultsMatchDotNet() {
        MessageTrackerOptions o = MessageTrackerOptions.builder().build();
        assertThat(o.serviceName()).isEqualTo("MessageBus");
        assertThat(o.callerName()).isEqualTo(TrackingDefaults.CALLER_NAME);
        assertThat(o.verbosity()).isEqualTo(TrackingVerbosity.DETAILED);
        assertThat(o.setupVerbosity()).isNull();
        assertThat(o.actionVerbosity()).isNull();
        assertThat(o.trackDuringSetup()).isTrue();
        assertThat(o.trackDuringAction()).isTrue();
        assertThat(o.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);
        assertThat(o.callerDependencyCategory()).isNull();
        assertThat(o.testInfoFetcher()).isNull();
        assertThat(o.payloadSerializer()).isNotNull();
        // The three Tier-2 fields.
        assertThat(o.currentStepTypeFetcher()).isNull();
        assertThat(o.useHttpContextCorrelation()).isFalse();
        assertThat(o.serializerOptions()).isNull();
    }

    @Test
    void builderSetsTheTierTwoFields() {
        MessageTrackerOptions o = MessageTrackerOptions.builder()
            .currentStepTypeFetcher(() -> "When")
            .useHttpContextCorrelation(true)
            .build();
        assertThat(o.currentStepTypeFetcher().get()).isEqualTo("When");
        assertThat(o.useHttpContextCorrelation()).isTrue();
    }

    @Test
    void serializerOptionsDrivesThePayloadSerializer() {
        TrackingSerializerOptions opts = TrackingSerializerOptions.builder().writeIndented(false).build();
        MessageTrackerOptions o = MessageTrackerOptions.builder().serializerOptions(opts).build();

        assertThat(o.serializerOptions()).isSameAs(opts);
        // The named options actually back the serialization seam.
        String json = o.payloadSerializer().apply(java.util.Map.of("k", "v"));
        assertThat(json).isEqualTo("{\"k\":\"v\"}");
    }

    @Test
    void customSerializerFunctionSupersedesNamedOptions() {
        MessageTrackerOptions o = MessageTrackerOptions.builder()
            .serializerOptions(TrackingSerializerOptions.builder().build())
            .payloadSerializer(p -> "CUSTOM:" + p)
            .build();
        assertThat(o.serializerOptions()).isNull();
        assertThat(o.payloadSerializer().apply("x")).isEqualTo("CUSTOM:x");
    }
}
