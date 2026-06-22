package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.diagram.plantuml.DiagramOptions;
import io.kronikol.diagram.plantuml.FocusDeEmphasis;
import io.kronikol.diagram.plantuml.FocusEmphasis;
import java.util.List;
import java.util.Set;
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

    @Test
    void advancedDiagramOptionsAreFirstClassAndThreadIntoDiagram() {
        ReportOptions opts = ReportOptions.defaults()
            .withSeparateSetup(true).withHighlightSetup(false).withSetupHighlightColor("#ABCDEF")
            .withExcludedHeaders(List.of("Authorization"))
            .withPlantUmlTheme("cyborg")
            .withFocusEmphasis(Set.of(FocusEmphasis.COLORED))
            .withFocusDeEmphasis(Set.of(FocusDeEmphasis.HIDDEN));

        DiagramOptions d = opts.diagram();
        assertThat(d.separateSetup()).isTrue();
        assertThat(d.highlightSetup()).isFalse();
        assertThat(d.setupHighlightColor()).isEqualTo("#ABCDEF");
        assertThat(d.excludedHeaders()).containsExactly("Authorization");
        assertThat(d.plantUmlTheme()).isEqualTo("cyborg");
        assertThat(d.focusEmphasis()).containsExactly(FocusEmphasis.COLORED);
        assertThat(d.focusDeEmphasis()).containsExactly(FocusDeEmphasis.HIDDEN);
        assertThat(opts.arrowColors()).isTrue(); // unrelated options untouched
    }

    @Test
    void readsDiagramOptionsFromSystemProperties() {
        System.setProperty(ReportOptions.SEPARATE_SETUP_PROPERTY, "true");
        System.setProperty(ReportOptions.SETUP_HIGHLIGHT_COLOR_PROPERTY, "#123456");
        System.setProperty(ReportOptions.EXCLUDED_HEADERS_PROPERTY, "Authorization, X-Trace");
        System.setProperty(ReportOptions.FOCUS_DE_EMPHASIS_PROPERTY, "smaller_text,hidden");
        try {
            DiagramOptions d = ReportOptions.fromSystemProperties().diagram();
            assertThat(d.separateSetup()).isTrue();
            assertThat(d.setupHighlightColor()).isEqualTo("#123456");
            assertThat(d.excludedHeaders()).containsExactly("Authorization", "X-Trace");
            assertThat(d.focusDeEmphasis())
                .containsExactlyInAnyOrder(FocusDeEmphasis.SMALLER_TEXT, FocusDeEmphasis.HIDDEN);
            assertThat(d.highlightSetup()).isTrue(); // unset → .NET default
        } finally {
            System.clearProperty(ReportOptions.SEPARATE_SETUP_PROPERTY);
            System.clearProperty(ReportOptions.SETUP_HIGHLIGHT_COLOR_PROPERTY);
            System.clearProperty(ReportOptions.EXCLUDED_HEADERS_PROPERTY);
            System.clearProperty(ReportOptions.FOCUS_DE_EMPHASIS_PROPERTY);
        }
    }
}
