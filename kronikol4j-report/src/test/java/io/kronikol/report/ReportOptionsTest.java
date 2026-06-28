package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.diagram.plantuml.DiagramOptions;
import io.kronikol.diagram.plantuml.FocusDeEmphasis;
import io.kronikol.diagram.plantuml.FocusEmphasis;
import io.kronikol.report.data.ReportDataFormat;
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
    void testRunReportDataFormatScalarMirrorsDotNet() {
        // .NET's TestRunReportDataFormat defaults to JSON; the Java dataFormats set generalizes it.
        assertThat(ReportOptions.defaults().testRunReportDataFormat()).isEqualTo(ReportDataFormat.JSON);

        ReportOptions xml = ReportOptions.defaults().withTestRunReportDataFormat(ReportDataFormat.XML);
        assertThat(xml.testRunReportDataFormat()).isEqualTo(ReportDataFormat.XML);
        assertThat(xml.dataFormats()).containsExactly(ReportDataFormat.XML); // sets the single emitted format

        // When several formats are configured, the scalar view reports the first (insertion order).
        ReportOptions multi = ReportOptions.defaults()
            .withDataFormats(new java.util.LinkedHashSet<>(List.of(ReportDataFormat.YAML, ReportDataFormat.JSON)));
        assertThat(multi.testRunReportDataFormat()).isEqualTo(ReportDataFormat.YAML);
    }

    @Test
    void reportControlOptionsDefaultsAndWithers() {
        ReportControlOptions c = ReportOptions.defaults().control();
        assertThat(c.testRunReportTitle()).isNull();                  // .NET default: auto-derived (caller title)
        assertThat(c.htmlReportFileName()).isEqualTo("TestRunReport"); // .NET HtmlTestRunReportFileName default
        assertThat(c.resolveTitle("Fallback")).isEqualTo("Fallback"); // null override → caller's title

        ReportOptions opts = ReportOptions.defaults()
            .withTestRunReportTitle("Nightly").withHtmlReportFileName("MyReport");
        assertThat(opts.control().testRunReportTitle()).isEqualTo("Nightly");
        assertThat(opts.control().resolveTitle("Fallback")).isEqualTo("Nightly"); // override wins
        assertThat(opts.control().htmlReportFileName()).isEqualTo("MyReport");
        assertThat(opts.diagram()).isEqualTo(ReportOptions.defaults().diagram()); // unrelated options untouched

        // blank file name falls back to the .NET default
        assertThat(ReportControlOptions.DEFAULTS.withHtmlReportFileName("  ").htmlReportFileName())
            .isEqualTo("TestRunReport");
    }

    @Test
    void readsReportControlOptionsFromSystemProperties() {
        System.setProperty(ReportOptions.REPORT_TITLE_PROPERTY, "CI Run");
        System.setProperty(ReportOptions.HTML_FILE_NAME_PROPERTY, "Combined");
        System.setProperty(ReportOptions.GENERATE_COMPONENT_DIAGRAM_PROPERTY, "false");
        try {
            ReportControlOptions c = ReportOptions.fromSystemProperties().control();
            assertThat(c.testRunReportTitle()).isEqualTo("CI Run");
            assertThat(c.htmlReportFileName()).isEqualTo("Combined");
            assertThat(c.generateComponentDiagram()).isFalse();
        } finally {
            System.clearProperty(ReportOptions.REPORT_TITLE_PROPERTY);
            System.clearProperty(ReportOptions.HTML_FILE_NAME_PROPERTY);
            System.clearProperty(ReportOptions.GENERATE_COMPONENT_DIAGRAM_PROPERTY);
        }
    }

    @Test
    void generateComponentDiagramDefaultsTrueAndWithers() {
        assertThat(ReportOptions.defaults().control().generateComponentDiagram()).isTrue(); // .NET default true
        ReportOptions off = ReportOptions.defaults().withGenerateComponentDiagram(false);
        assertThat(off.control().generateComponentDiagram()).isFalse();
        assertThat(off.control().htmlReportFileName()).isEqualTo("TestRunReport"); // unrelated control flags kept
    }

    @Test
    void generateMergeableDataDefaultsFalseAndWithers() {
        assertThat(ReportOptions.defaults().control().generateMergeableData()).isFalse(); // .NET default false
        ReportOptions on = ReportOptions.defaults().withGenerateMergeableData(true);
        assertThat(on.control().generateMergeableData()).isTrue();
        assertThat(on.control().generateComponentDiagram()).isTrue(); // unrelated control flags kept

        System.setProperty(ReportOptions.GENERATE_MERGEABLE_DATA_PROPERTY, "true");
        try {
            assertThat(ReportOptions.fromSystemProperties().control().generateMergeableData()).isTrue();
        } finally {
            System.clearProperty(ReportOptions.GENERATE_MERGEABLE_DATA_PROPERTY);
        }
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
