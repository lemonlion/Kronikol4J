package io.kronikol.report.ci;

import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a sample CI markdown summary (all-passing / all-failing / mixed) and appends it to the GitHub
 * step summary (<code>$GITHUB_STEP_SUMMARY</code>), else prints to stdout — the Java equivalent of the .NET
 * {@code ci-summary-preview} workflow's dogfooding step, exercising {@link CiSummaryGenerator}. Lives in
 * test sources so it is never published in the library jars.
 */
public final class CiSummaryPreview {

    private CiSummaryPreview() {
    }

    public static void main(String[] args) throws IOException {
        String variant = args.length > 0 ? args[0] : "mixed";
        Feature feature = switch (variant) {
            case "allPassing" -> sampleFeature("All passing", 10, 0);
            case "allFailing" -> sampleFeature("All failing", 0, 10);
            default -> sampleFeature("Mixed", 5, 5);
        };
        Instant start = Instant.parse("2024-01-15T10:00:00Z");
        String md = "## CI Summary preview — " + variant + "\n\n"
            + CiSummaryGenerator.generateMarkdown(List.of(feature), List.of(), List.of(), start,
                start.plusSeconds(12));

        String summaryFile = System.getenv("GITHUB_STEP_SUMMARY");
        if (summaryFile != null && !summaryFile.isBlank()) {
            Files.writeString(Path.of(summaryFile), md + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            System.out.println(md);
        }
    }

    private static Feature sampleFeature(String name, int passed, int failed) {
        List<Scenario> scenarios = new ArrayList<>();
        for (int i = 1; i <= passed; i++) {
            scenarios.add(Scenario.builder("Passing scenario " + i, "p" + i, ExecutionStatus.PASSED)
                .isHappyPath(i == 1).durationMs(40L + i * 5L).build());
        }
        for (int i = 1; i <= failed; i++) {
            scenarios.add(Scenario.builder("Failing scenario " + i, "f" + i, ExecutionStatus.FAILED)
                .durationMs(30L + i * 4L)
                .error("expected 200 but got 500 (case " + i + ")")
                .errorStackTrace("at Sample.run()\n  at Test.invoke()").build());
        }
        return new Feature(name, scenarios);
    }
}
