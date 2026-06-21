package io.kronikol.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edge cases the rich parity corpus doesn't exercise: absent diagram/log lookups, and a
 *  whole-number duration (which System.Text.Json writes as {@code 0}, not {@code 0.0}). */
class ReportDataSerializerTest {

    private static ReportData minimal() {
        var feature = new Feature("Checkout", List.of(Scenario.passed("Checkout succeeds", "s1")));
        return new ReportData("v1.2.3", Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-01T00:00:01Z"), List.of(feature), null, null);
    }

    @Test
    void omitsDiagramsAndInteractionsWhenLookupsAreAbsent() {
        ReportData data = minimal();
        assertThat(ReportDataSerializer.toJson(data))
            .doesNotContain("diagrams").doesNotContain("httpInteractions");
        assertThat(ReportDataSerializer.toXml(data))
            .doesNotContain("Diagrams").doesNotContain("HttpInteractions");
        assertThat(ReportDataSerializer.toYaml(data))
            .doesNotContain("Diagrams").doesNotContain("HttpInteractions");
    }

    @Test
    void wholeSecondDurationFormatsLikeDotNet() {
        // Scenario.passed → durationMs 0 → JSON "0" (not "0.0"), XML/YAML "0.000".
        ReportData data = minimal();
        assertThat(ReportDataSerializer.toJson(data)).contains("\"durationSeconds\": 0,");
        assertThat(ReportDataSerializer.toXml(data)).contains("<DurationSeconds>0.000</DurationSeconds>");
        assertThat(ReportDataSerializer.toYaml(data)).contains("DurationSeconds: 0.000");
    }
}
