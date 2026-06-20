package io.kronikol.report;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.diagram.plantuml.PlantUmlTextEncoder;
import io.kronikol.report.html.HtmlEscaper;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the interactive HTML report from scenarios + tracked logs (plan §1 Seam C / Phase 3).
 *
 * <p>Browser-only rendering (plan §3.5): each diagram's PlantUML is encoded into a JS data map and a
 * {@code .plantuml-browser} element is emitted for the client-side renderer. The externalized
 * {@code plantuml-render.js} asset (a .NET-side §4.2 prep task) performs the WASM render; until it is
 * bundled, the collapsible raw PlantUML beneath each diagram keeps the report useful. All output uses
 * {@code \n} and the {@link HtmlEscaper} parity shim (§4.4/§6.5).
 */
public final class HtmlReportGenerator {

    private static final String NL = "\n";

    /** The rendered report and where it was written. */
    public record GeneratedReport(String html, Path htmlFile) {
    }

    private HtmlReportGenerator() {
    }

    public static GeneratedReport generate(List<Feature> features,
                                           List<RequestResponseLog> logs,
                                           Path outputDir,
                                           String title) throws IOException {
        Map<String, String> diagramByTestId = new HashMap<>();
        for (PlantUmlForTest p : PlantUmlCreator.create(logs)) {
            if (!p.diagrams().isEmpty()) {
                diagramByTestId.put(p.testId(), p.diagrams().get(0)); // one per test (client-side splitting)
            }
        }

        StringBuilder body = new StringBuilder(2048);
        StringBuilder dataMap = new StringBuilder(512);
        int diagramCounter = 0;
        int passed = 0;
        int failed = 0;
        int totalScenarios = 0;

        for (Feature feature : features) {
            body.append("  <section class=\"feature\">").append(NL);
            body.append("    <h2>").append(HtmlEscaper.encode(feature.displayName())).append("</h2>").append(NL);
            for (Scenario scenario : feature.scenarios()) {
                totalScenarios++;
                if (scenario.status() == ExecutionStatus.PASSED) {
                    passed++;
                } else if (scenario.status() == ExecutionStatus.FAILED) {
                    failed++;
                }
                String statusClass = scenario.status().name().toLowerCase(java.util.Locale.ROOT);
                body.append("    <div class=\"scenario ").append(statusClass).append("\">").append(NL);
                body.append("      <h3>").append(HtmlEscaper.encode(scenario.name()))
                    .append(" <span class=\"badge ").append(statusClass).append("\">")
                    .append(scenario.status().name()).append("</span>");
                if (scenario.durationMs() > 0) {
                    body.append(" <span class=\"dur\">").append(scenario.durationMs()).append(" ms</span>");
                }
                body.append("</h3>").append(NL);

                if (scenario.error() != null && !scenario.error().isBlank()) {
                    body.append("      <pre class=\"error\">")
                        .append(HtmlEscaper.encode(scenario.error())).append("</pre>").append(NL);
                }

                String plantUml = diagramByTestId.get(scenario.testId());
                if (plantUml != null) {
                    String id = "puml-" + (diagramCounter++);
                    String encoded = PlantUmlTextEncoder.encode(plantUml);
                    dataMap.append("window.kronikolDiagrams[\"").append(id).append("\"] = \"")
                        .append(encoded).append("\";").append(NL);
                    body.append("      <div class=\"plantuml-browser\" id=\"").append(id)
                        .append("\" data-diagram-id=\"").append(id).append("\"></div>").append(NL);
                    body.append("      <details class=\"puml-src\"><summary>PlantUML source</summary><pre>")
                        .append(HtmlEscaper.encode(plantUml)).append("</pre></details>").append(NL);
                }
                body.append("    </div>").append(NL);
            }
            body.append("  </section>").append(NL);
        }

        String summary = totalScenarios + " scenarios · " + passed + " passed · " + failed + " failed";
        String html = document(HtmlEscaper.encode(title), summary, dataMap.toString(), body.toString());

        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("TestRunReport.html");
        Files.writeString(file, html, StandardCharsets.UTF_8);
        return new GeneratedReport(html, file);
    }

    private static String document(String title, String summary, String dataMap, String body) {
        return "<!DOCTYPE html>" + NL
            + "<html lang=\"en\">" + NL
            + "<head>" + NL
            + "<meta charset=\"utf-8\">" + NL
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" + NL
            + "<title>" + title + "</title>" + NL
            + "<style>" + NL + STYLES + NL + "</style>" + NL
            + "<script>" + NL + "window.kronikolDiagrams = window.kronikolDiagrams || {};" + NL
            + dataMap + "</script>" + NL
            + "<!-- Browser rendering: the externalized plantuml-render.js (plan §4.2) renders each"
            + " .plantuml-browser element from window.kronikolDiagrams. -->" + NL
            + "</head>" + NL
            + "<body>" + NL
            + "<h1>" + title + "</h1>" + NL
            + "<div class=\"summary\">" + summary + "</div>" + NL
            + body
            + "</body>" + NL
            + "</html>" + NL;
    }

    private static final String STYLES = String.join(NL,
        "body { font-family: system-ui, sans-serif; margin: 2rem; color: #222; }",
        "h1 { font-size: 1.6rem; }",
        ".summary { color: #555; margin-bottom: 1.5rem; }",
        ".feature { margin-bottom: 2rem; }",
        ".scenario { border: 1px solid #e0e0e0; border-radius: 6px; padding: 1rem; margin: 0.75rem 0; }",
        ".badge { font-size: 0.75rem; padding: 0.15rem 0.5rem; border-radius: 10px; color: #fff; }",
        ".badge.passed { background: #2e7d32; }",
        ".badge.failed { background: #c62828; }",
        ".badge.skipped, .badge.inconclusive { background: #757575; }",
        ".dur { font-size: 0.75rem; color: #888; }",
        ".error { background: #fdecea; color: #611a15; padding: 0.5rem; overflow:auto; }",
        ".puml-src pre { background: #f6f8fa; padding: 0.5rem; overflow:auto; font-size: 0.8rem; }",
        ".plantuml-browser { min-height: 1rem; }");
}
