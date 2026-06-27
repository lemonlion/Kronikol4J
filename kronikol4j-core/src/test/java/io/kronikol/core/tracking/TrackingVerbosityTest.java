package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestPhaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the shared verbosity levels and their semantics against the .NET source of truth.
 *
 * <p>.NET exposes the identical three-level scale under two per-tracker enums
 * ({@code MessageTrackerVerbosity} and {@code SqlTrackingVerbosityLevel}), both
 * {@code Raw / Detailed / Summarised}. Java unifies them into one {@link TrackingVerbosity}.
 * The two extra levels ({@code HeadersOnly} / {@code None}) named in the parity roadmap do not
 * exist anywhere in the .NET source, so they are deliberately not modelled.
 */
class TrackingVerbosityTest {

    @AfterEach
    void cleanup() {
        TestPhaseContext.reset();
    }

    @Test
    void hasExactlyTheThreeDotNetLevelsInOrder() {
        assertThat(TrackingVerbosity.values())
            .containsExactly(
                TrackingVerbosity.RAW,
                TrackingVerbosity.DETAILED,
                TrackingVerbosity.SUMMARISED);
    }

    @Test
    void onlySummarisedOmitsThePayload() {
        // .NET: every tracker treats `== Summarised` as "omit message payload / SQL text"; others include it.
        assertThat(TrackingVerbosity.RAW.includesPayload()).isTrue();
        assertThat(TrackingVerbosity.DETAILED.includesPayload()).isTrue();
        assertThat(TrackingVerbosity.SUMMARISED.includesPayload()).isFalse();
    }

    @Test
    void onlyRawIncludesUnclassifiedRawDetail() {
        // .NET: Raw keeps full SQL text + parameters + all headers; Detailed classifies; Summarised strips.
        assertThat(TrackingVerbosity.RAW.includesRawDetail()).isTrue();
        assertThat(TrackingVerbosity.DETAILED.includesRawDetail()).isFalse();
        assertThat(TrackingVerbosity.SUMMARISED.includesRawDetail()).isFalse();
    }

    @Test
    void defaultLevelMatchesDotNetTrackerDefault() {
        // .NET MessageTrackerOptions.Verbosity defaults to Detailed.
        assertThat(TrackingVerbosity.DEFAULT).isEqualTo(TrackingVerbosity.DETAILED);
    }

    @Test
    void resolvesThroughPhaseConfigurationLikeTheDotNetTrackers() {
        // The per-tracker resolution logic (base + setup/action override, keyed on phase) is the
        // generic PhaseConfiguration.effectiveVerbosity — confirm it composes with this enum.
        TestPhaseContext.set(TestPhase.SETUP);
        assertThat(PhaseConfiguration.effectiveVerbosity(
                TrackingVerbosity.DETAILED, TrackingVerbosity.RAW, TrackingVerbosity.SUMMARISED))
            .isEqualTo(TrackingVerbosity.RAW);

        assertThat(PhaseConfiguration.effectiveVerbosity(
                TrackingVerbosity.DETAILED, null, TrackingVerbosity.SUMMARISED))
            .isEqualTo(TrackingVerbosity.DETAILED);

        TestPhaseContext.set(TestPhase.ACTION);
        assertThat(PhaseConfiguration.effectiveVerbosity(
                TrackingVerbosity.DETAILED, TrackingVerbosity.RAW, TrackingVerbosity.SUMMARISED))
            .isEqualTo(TrackingVerbosity.SUMMARISED);

        TestPhaseContext.set(TestPhase.UNKNOWN);
        assertThat(PhaseConfiguration.effectiveVerbosity(
                TrackingVerbosity.DETAILED, TrackingVerbosity.RAW, TrackingVerbosity.SUMMARISED))
            .isEqualTo(TrackingVerbosity.DETAILED);
    }
}
