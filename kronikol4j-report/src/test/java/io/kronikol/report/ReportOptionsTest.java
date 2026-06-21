package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportOptionsTest {

    @Test
    void defaultsMatchDotNet() {
        ReportOptions defaults = ReportOptions.defaults();
        assertThat(defaults.arrowColors()).isTrue();         // .NET: arrows coloured per dependency
        assertThat(defaults.participantColors()).isFalse();  // .NET: participants uncoloured
    }

    @Test
    void withersAreIndependentAndImmutable() {
        ReportOptions base = new ReportOptions(true, false);

        assertThat(base.withParticipantColors(true)).isEqualTo(new ReportOptions(true, true));
        assertThat(base.withArrowColors(false)).isEqualTo(new ReportOptions(false, false));
        assertThat(base).isEqualTo(new ReportOptions(true, false)); // original unchanged (immutable)
    }

    @Test
    void readsBothFlagsFromSystemProperties() {
        System.setProperty(ReportOptions.ARROW_COLORS_PROPERTY, "true");
        System.setProperty(ReportOptions.PARTICIPANT_COLORS_PROPERTY, "true");
        try {
            assertThat(ReportOptions.fromSystemProperties()).isEqualTo(new ReportOptions(true, true));
        } finally {
            System.clearProperty(ReportOptions.ARROW_COLORS_PROPERTY);
            System.clearProperty(ReportOptions.PARTICIPANT_COLORS_PROPERTY);
        }
    }

    @Test
    void absentSystemPropertiesFallBackToDotNetDefaults() {
        // Neither property set in this test → the .NET defaults (arrows on, participants off).
        assertThat(ReportOptions.fromSystemProperties()).isEqualTo(ReportOptions.defaults());
    }
}
