package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TestPhase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhaseConfigurationTest {

    @AfterEach
    void cleanup() {
        TestPhaseContext.reset();
    }

    @Test
    void unknownPhaseAlwaysTracks() {
        TestPhaseContext.set(TestPhase.UNKNOWN);
        assertThat(PhaseConfiguration.shouldTrack(false, false)).isTrue();
    }

    @Test
    void setupAndActionTogglesAreHonoured() {
        TestPhaseContext.set(TestPhase.SETUP);
        assertThat(PhaseConfiguration.shouldTrack(true, false)).isTrue();
        assertThat(PhaseConfiguration.shouldTrack(false, true)).isFalse();

        TestPhaseContext.set(TestPhase.ACTION);
        assertThat(PhaseConfiguration.shouldTrack(false, true)).isTrue();
        assertThat(PhaseConfiguration.shouldTrack(true, false)).isFalse();
    }

    @Test
    void effectiveVerbosityFallsBackToDefaultWhenUnset() {
        TestPhaseContext.set(TestPhase.SETUP);
        assertThat(PhaseConfiguration.effectiveVerbosity("default", "setup", "action")).isEqualTo("setup");
        assertThat(PhaseConfiguration.effectiveVerbosity("default", null, "action")).isEqualTo("default");

        TestPhaseContext.set(TestPhase.UNKNOWN);
        assertThat(PhaseConfiguration.effectiveVerbosity("default", "setup", "action")).isEqualTo("default");
    }

    @Test
    void resolvePhaseFromStepTypeMapsGivenAndButToSetup() {
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("Given")).isEqualTo(TestPhase.SETUP);
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("And")).isEqualTo(TestPhase.SETUP);
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("But")).isEqualTo(TestPhase.SETUP);
    }

    @Test
    void resolvePhaseFromStepTypeMapsWhenThenToAction() {
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("When")).isEqualTo(TestPhase.ACTION);
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("Then")).isEqualTo(TestPhase.ACTION);
    }

    @Test
    void resolvePhaseFromStepTypeIsCaseInsensitiveAndPrefixMatched() {
        // .NET uses StartsWith(OrdinalIgnoreCase): "given that ..." and lower/upper all match.
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("given")).isEqualTo(TestPhase.SETUP);
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("GIVEN that the user exists"))
            .isEqualTo(TestPhase.SETUP);
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("whenever")).isEqualTo(TestPhase.ACTION);
    }

    @Test
    void resolvePhaseFromStepTypeReturnsUnknownForNullOrUnmatched() {
        assertThat(PhaseConfiguration.resolvePhaseFromStepType(null)).isEqualTo(TestPhase.UNKNOWN);
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("")).isEqualTo(TestPhase.UNKNOWN);
        assertThat(PhaseConfiguration.resolvePhaseFromStepType("Scenario")).isEqualTo(TestPhase.UNKNOWN);
    }
}
