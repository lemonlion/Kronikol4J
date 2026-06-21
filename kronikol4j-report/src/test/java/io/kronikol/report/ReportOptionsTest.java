package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportOptionsTest {

    @Test
    void defaultsAreNoColour() {
        ReportOptions defaults = ReportOptions.defaults();
        assertThat(defaults.arrowColors()).isFalse();
        assertThat(defaults.participantColors()).isFalse();
    }

    @Test
    void withersAreIndependentAndImmutable() {
        ReportOptions base = ReportOptions.defaults();
        ReportOptions arrows = base.withArrowColors(true);
        ReportOptions both = arrows.withParticipantColors(true);

        assertThat(base).isEqualTo(ReportOptions.defaults());     // unchanged
        assertThat(arrows).isEqualTo(new ReportOptions(true, false));
        assertThat(both).isEqualTo(new ReportOptions(true, true));
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
    void absentSystemPropertiesDefaultToOff() {
        // Neither property set in this test → both false.
        assertThat(ReportOptions.fromSystemProperties()).isEqualTo(ReportOptions.defaults());
    }
}
