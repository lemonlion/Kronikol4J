package io.kronikol.report.ci;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.diagram.plantuml.PlantUmlTextEncoder;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Parity for {@link CiSummaryGenerator} against the real .NET {@code CiSummaryGenerator.GenerateMarkdown}.
 * The metrics table + failed-scenario details are byte-stable; the diagram link's DEFLATE-encoded server
 * token is not byte-stable across runtimes (the gzip {@code puml-data} boundary), so it is masked for the
 * structural compare and proven separately by decoding it.
 */
class CiSummaryGeneratorTest {

    private static final Instant T0 = Instant.parse("2024-01-15T10:00:00Z");
    private static final Pattern SVG_TOKEN = Pattern.compile("/svg/([0-9A-Za-z_-]+)\\)");

    @Test
    void failedSummary_noDiagrams_isByteForByteIdenticalToDotNet() {
        Scenario passed = Scenario.builder("Loads home", "s0", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(40).build();
        Scenario failed1 = Scenario.builder("Login <fails> & burns", "s1", ExecutionStatus.FAILED)
            .durationMs(50).error("expected 200 | got 500")
            .errorStackTrace("at Login.Do()\n  at Test.Run()").build();
        Scenario failed2 = Scenario.builder("Checkout fails", "s2", ExecutionStatus.FAILED)
            .durationMs(20).error("boom").build();
        Scenario skipped = Scenario.builder("Deferred", "s3", ExecutionStatus.SKIPPED).durationMs(0).build();
        Feature feature = new Feature("Web", List.of(passed, failed1, failed2, skipped));

        String md = CiSummaryGenerator.generateMarkdown(
            List.of(feature), List.of(), List.of(), T0, T0.plusSeconds(5));

        assertThat(md).isEqualTo(readFixture("ci-summary-failed.md"));
    }

    @Test
    void passedSummary_withDiagram_structureMatchesDotNet_andTokenDecodes() {
        Scenario s1 = Scenario.builder("Places an order", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(120).build();
        Feature feature = new Feature("Checkout", List.of(s1));
        String puml = "@startuml\nTest -> orderService: POST: http://svc/orders\n"
            + "orderService --> Test: 201\n@enduml";
        List<CiDiagram> diagrams = List.of(new CiDiagram("s1", puml));

        String md = CiSummaryGenerator.generateMarkdown(
            List.of(feature), diagrams, diagrams, T0, T0.plusSeconds(65));

        // Mask the non-byte-stable DEFLATE token, then byte-compare the whole structure (incl. the raw
        // ```plantuml source block).
        assertThat(maskSvgToken(md)).isEqualTo(maskSvgToken(readFixture("ci-summary-diagrams.md")));

        // The Java token decodes to the URL-deactivated source (proves the encoder integration).
        Matcher m = SVG_TOKEN.matcher(md);
        assertThat(m.find()).isTrue();
        assertThat(PlantUmlTextEncoder.decode(m.group(1)))
            .isEqualTo(CiSummaryGenerator.deactivateUrls(puml))
            .contains("http&#58;//svc/orders"); // the URL was deactivated before encoding
    }

    @Test
    void formatDuration_matchesDotNetThresholds() {
        assertThat(CiSummaryGenerator.formatDuration(Duration.ofMillis(0))).isEqualTo("0ms");
        assertThat(CiSummaryGenerator.formatDuration(Duration.ofMillis(999))).isEqualTo("999ms");
        assertThat(CiSummaryGenerator.formatDuration(Duration.ofMillis(1000))).isEqualTo("1s");
        assertThat(CiSummaryGenerator.formatDuration(Duration.ofMillis(59000))).isEqualTo("59s");
        assertThat(CiSummaryGenerator.formatDuration(Duration.ofMillis(65000))).isEqualTo("1m 5s");
        assertThat(CiSummaryGenerator.formatDuration(Duration.ofMillis(125000))).isEqualTo("2m 5s");
        assertThat(CiSummaryGenerator.formatDuration(Duration.ofMillis(-5000))).isEqualTo("5s"); // abs
    }

    private static String maskSvgToken(String md) {
        return SVG_TOKEN.matcher(md).replaceAll("/svg/<MASKED>)");
    }

    private static String readFixture(String name) {
        try (InputStream in = CiSummaryGeneratorTest.class.getResourceAsStream("/parity/" + name)) {
            assertThat(in).as("fixture /parity/" + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
