package io.kronikol.report.ci;

import io.kronikol.diagram.plantuml.PlantUmlTextEncoder;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a markdown summary of the test run (pass/fail counts, failure details with diagrams, and
 * execution timing) — a faithful port of the .NET {@code CiSummaryGenerator.GenerateMarkdown}, intended
 * for a CI step summary (e.g. {@code $GITHUB_STEP_SUMMARY}).
 *
 * <p>Output uses {@code "\n"} line endings (the .NET {@code StringBuilder.AppendLine} CRLF is normalised
 * to LF exactly as the captured goldens are). The embedded diagram links use the PlantUML-server text
 * encoding ({@link PlantUmlTextEncoder}); that DEFLATE payload is not byte-stable across runtimes (the
 * same boundary as the report's gzip {@code puml-data} island), so only the markdown structure is
 * byte-locked — the encoded token decodes to the (URL-deactivated) source.
 */
public final class CiSummaryGenerator {

    /** The default public PlantUML server base (matches the .NET default argument). */
    public static final String DEFAULT_PLANTUML_SERVER = "https://plantuml.com/plantuml";

    private CiSummaryGenerator() {
    }

    public static String generateMarkdown(List<Feature> features, List<CiDiagram> truncatedDiagrams,
                                          List<CiDiagram> fullDiagrams, Instant startRunTime, Instant endRunTime) {
        return generateMarkdown(features, truncatedDiagrams, fullDiagrams, startRunTime, endRunTime, 10,
            DEFAULT_PLANTUML_SERVER);
    }

    public static String generateMarkdown(List<Feature> features, List<CiDiagram> truncatedDiagrams,
                                          List<CiDiagram> fullDiagrams, Instant startRunTime, Instant endRunTime,
                                          int maxDiagrams, String plantUmlServerBaseUrl) {
        StringBuilder sb = new StringBuilder();
        List<Scenario> allScenarios = new ArrayList<>();
        for (Feature f : features) {
            allScenarios.addAll(f.scenarios());
        }
        int passed = count(allScenarios, ExecutionStatus.PASSED);
        int failed = count(allScenarios, ExecutionStatus.FAILED);
        int skipped = count(allScenarios, ExecutionStatus.SKIPPED);
        int total = allScenarios.size();
        boolean hasFailed = failed > 0;
        String status = hasFailed ? "❌ Failed" : "✅ Passed";
        String duration = formatDuration(Duration.between(startRunTime, endRunTime));

        line(sb, "# Diagrammed Test Run Summary");
        line(sb);
        line(sb, "| Metric | Value |");
        line(sb, "|---|---|");
        line(sb, "| Status | " + status + " |");
        line(sb, "| Scenarios | " + total + " |");
        line(sb, "| Passed | " + passed + " |");
        line(sb, "| Failed | " + failed + " |");
        line(sb, "| Skipped | " + skipped + " |");
        line(sb, "| Duration | " + duration + " |");
        line(sb);

        Map<String, List<CiDiagram>> truncatedByTestId = groupByTestId(truncatedDiagrams);
        Map<String, List<CiDiagram>> fullByTestId = groupByTestId(fullDiagrams);

        if (hasFailed) {
            appendFailedScenarios(sb, features, truncatedByTestId, fullByTestId, failed, maxDiagrams,
                plantUmlServerBaseUrl);
        } else {
            appendPassedDiagrams(sb, features, truncatedByTestId, fullByTestId, total, maxDiagrams,
                plantUmlServerBaseUrl);
        }
        return sb.toString();
    }

    private static void appendFailedScenarios(StringBuilder sb, List<Feature> features,
                                              Map<String, List<CiDiagram>> truncatedByTestId,
                                              Map<String, List<CiDiagram>> fullByTestId, int totalFailed,
                                              int maxDiagrams, String plantUmlServerBaseUrl) {
        line(sb, "## ❌ Failed Scenarios (" + totalFailed + ")");
        line(sb);

        int shown = 0;
        for (Feature feature : features) {
            for (Scenario scenario : feature.scenarios()) {
                if (scenario.status() != ExecutionStatus.FAILED) {
                    continue;
                }
                if (shown >= maxDiagrams) {
                    break;
                }
                line(sb, "<details><summary>❌ <strong>" + escapeHtml(feature.displayName()) + " — "
                    + escapeHtml(scenario.name()) + "</strong></summary>");
                line(sb);

                if (scenario.error() != null && !scenario.error().isEmpty()) {
                    line(sb, "**Error:** " + escapeMarkdown(scenario.error()));
                    line(sb);
                }
                if (scenario.errorStackTrace() != null && !scenario.errorStackTrace().isEmpty()) {
                    line(sb, "<details open><summary>Stack Trace</summary>");
                    line(sb);
                    line(sb, "```");
                    line(sb, scenario.errorStackTrace());
                    line(sb, "```");
                    line(sb);
                    line(sb, "</details>");
                    line(sb);
                }

                appendDiagramImages(sb, get(truncatedByTestId, scenario.testId()),
                    get(fullByTestId, scenario.testId()), plantUmlServerBaseUrl);

                line(sb, "</details>");
                line(sb);
                shown++;
            }
            if (shown >= maxDiagrams) {
                break;
            }
        }

        int remaining = totalFailed - shown;
        if (remaining > 0) {
            line(sb, "*" + remaining + " more failed scenario(s) not shown — see full report*");
            line(sb);
        }
    }

    private static void appendPassedDiagrams(StringBuilder sb, List<Feature> features,
                                             Map<String, List<CiDiagram>> truncatedByTestId,
                                             Map<String, List<CiDiagram>> fullByTestId, int totalScenarios,
                                             int maxDiagrams, String plantUmlServerBaseUrl) {
        List<Feature> ownerFeatures = new ArrayList<>();
        List<Scenario> withDiagrams = new ArrayList<>();
        for (Feature f : features) {
            for (Scenario s : f.scenarios()) {
                if (!get(truncatedByTestId, s.testId()).isEmpty()) {
                    ownerFeatures.add(f);
                    withDiagrams.add(s);
                }
            }
        }
        if (withDiagrams.isEmpty()) {
            return;
        }

        line(sb, "## Sequence Diagrams");
        line(sb);

        int shown = 0;
        for (int i = 0; i < withDiagrams.size(); i++) {
            if (shown >= maxDiagrams) {
                break;
            }
            Feature feature = ownerFeatures.get(i);
            Scenario scenario = withDiagrams.get(i);
            line(sb, "<details><summary>✅ <strong>" + escapeHtml(feature.displayName()) + " — "
                + escapeHtml(scenario.name()) + "</strong></summary>");
            line(sb);
            appendDiagramImages(sb, get(truncatedByTestId, scenario.testId()),
                get(fullByTestId, scenario.testId()), plantUmlServerBaseUrl);

            line(sb, "</details>");
            line(sb);
            shown++;
        }

        int remaining = totalScenarios - shown;
        if (remaining > 0) {
            line(sb, "*" + remaining + " more scenario(s) not shown — see full report*");
            line(sb);
        }
    }

    private static void appendDiagramImages(StringBuilder sb, List<CiDiagram> truncatedList,
                                            List<CiDiagram> fullList, String plantUmlServerBaseUrl) {
        boolean wasTruncated = truncatedList.size() != fullList.size()
            || !codeBehinds(truncatedList).equals(codeBehinds(fullList));

        if (wasTruncated) {
            boolean isMultiPart = truncatedList.size() > 1;
            for (int i = 0; i < truncatedList.size(); i++) {
                String partSuffix = isMultiPart ? " (Part " + (i + 1) + ")" : "";
                String encoded = PlantUmlTextEncoder.encode(deactivateUrls(truncatedList.get(i).codeBehind()));
                line(sb, "<details open><summary>Truncated Sequence Diagram" + partSuffix + "</summary>");
                line(sb);
                line(sb, "![diagram](" + plantUmlServerBaseUrl + "/svg/" + encoded + ")");
                line(sb);
                line(sb, "</details>");
                line(sb);
            }

            isMultiPart = fullList.size() > 1;
            for (int i = 0; i < fullList.size(); i++) {
                String partSuffix = isMultiPart ? " (Part " + (i + 1) + ")" : "";
                String encoded = PlantUmlTextEncoder.encode(deactivateUrls(fullList.get(i).codeBehind()));
                line(sb, "<details><summary>Full Sequence Diagram" + partSuffix + "</summary>");
                line(sb);
                line(sb, "![diagram](" + plantUmlServerBaseUrl + "/svg/" + encoded + ")");
                line(sb);
                line(sb, "</details>");
                line(sb);
            }

            for (int i = 0; i < fullList.size(); i++) {
                String partSuffix = isMultiPart ? " (Part " + (i + 1) + ")" : "";
                line(sb, "<details><summary>Full Sequence Diagram" + partSuffix + " - PlantUML</summary>");
                line(sb);
                line(sb, "```plantuml");
                line(sb, fullList.get(i).codeBehind());
                line(sb, "```");
                line(sb);
                line(sb, "</details>");
                line(sb);
            }
        } else {
            boolean isMultiPart = truncatedList.size() > 1;
            for (int i = 0; i < truncatedList.size(); i++) {
                String partSuffix = isMultiPart ? " (Part " + (i + 1) + ")" : "";
                String encoded = PlantUmlTextEncoder.encode(deactivateUrls(truncatedList.get(i).codeBehind()));
                if (isMultiPart) {
                    line(sb, "<details open><summary>Sequence Diagram" + partSuffix + "</summary>");
                    line(sb);
                    line(sb, "![diagram](" + plantUmlServerBaseUrl + "/svg/" + encoded + ")");
                    line(sb);
                    line(sb, "</details>");
                } else {
                    line(sb, "![diagram](" + plantUmlServerBaseUrl + "/svg/" + encoded + ")");
                }
                line(sb);
            }

            for (int i = 0; i < truncatedList.size(); i++) {
                String partSuffix = isMultiPart ? " (Part " + (i + 1) + ")" : "";
                String label = isMultiPart ? "Sequence Diagram" + partSuffix + " - PlantUML"
                    : "Sequence Diagram - PlantUML";
                line(sb, "<details><summary>" + label + "</summary>");
                line(sb);
                line(sb, "```plantuml");
                line(sb, truncatedList.get(i).codeBehind());
                line(sb, "```");
                line(sb);
                line(sb, "</details>");
                line(sb);
            }
        }
    }

    /**
     * Breaks URL patterns in PlantUML source so the server doesn't render them as SVG hyperlinks
     * (non-clickable inside img tags). Replaces {@code "://"} with {@code "&#58;//"} — PlantUML renders
     * {@code &#58;} as {@code ":"} but doesn't treat it as a protocol prefix (.NET {@code DeactivateUrls}).
     */
    static String deactivateUrls(String plantUml) {
        return plantUml.replaceAll("(https?)://", "$1&#58;//");
    }

    private static String escapeMarkdown(String text) {
        return text.replace("|", "\\|");
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Human-readable duration (.NET {@code FormatDuration}): {@code <1s} → {@code "{ms}ms"},
     * {@code <1m} → {@code "{s}s"}, else {@code "{m}m {s}s"} (the seconds component, 0–59).
     */
    static String formatDuration(Duration duration) {
        long millis = Math.abs(duration.toMillis());
        if (millis < 1000) {
            return millis + "ms";
        }
        if (millis < 60000) {
            return (millis / 1000) + "s";
        }
        return (millis / 60000) + "m " + ((millis / 1000) % 60) + "s";
    }

    private static int count(List<Scenario> scenarios, ExecutionStatus status) {
        int n = 0;
        for (Scenario s : scenarios) {
            if (s.status() == status) {
                n++;
            }
        }
        return n;
    }

    /** Groups diagram parts by test id, preserving first-seen order (mirrors .NET {@code ToLookup}). */
    private static Map<String, List<CiDiagram>> groupByTestId(List<CiDiagram> diagrams) {
        Map<String, List<CiDiagram>> map = new LinkedHashMap<>();
        for (CiDiagram d : diagrams) {
            map.computeIfAbsent(d.testId(), k -> new ArrayList<>()).add(d);
        }
        return map;
    }

    private static List<CiDiagram> get(Map<String, List<CiDiagram>> map, String testId) {
        return map.getOrDefault(testId, List.of());
    }

    private static List<String> codeBehinds(List<CiDiagram> diagrams) {
        List<String> out = new ArrayList<>(diagrams.size());
        for (CiDiagram d : diagrams) {
            out.add(d.codeBehind());
        }
        return out;
    }

    private static void line(StringBuilder sb, String s) {
        sb.append(s).append('\n');
    }

    private static void line(StringBuilder sb) {
        sb.append('\n');
    }
}
