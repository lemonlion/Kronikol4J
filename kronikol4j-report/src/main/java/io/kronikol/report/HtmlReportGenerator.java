package io.kronikol.report;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.diagram.component.ComponentDiagramGenerator;
import io.kronikol.diagram.component.ComponentRelationship;
import io.kronikol.diagram.json.Json;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.report.html.HtmlEscaper;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the interactive HTML report from scenarios + tracked logs (plan §1 Seam C / Phase 3).
 *
 * <p>Browser-only rendering (plan §3.5): each diagram's PlantUML is placed in the
 * {@code #kronikol-diagrams} JSON map; the bundled {@code kronikol-render.js} loads PlantUML-WASM
 * from the CDN and renders each {@code .plantuml-browser} element client-side. A collapsible raw
 * PlantUML block beneath each diagram keeps the report useful without scripts. All output uses
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
        return generate(features, logs, outputDir, title, ReportOptions.defaults());
    }

    /** As {@link #generate(List, List, Path, String)}, honouring the diagram colour options. */
    public static GeneratedReport generate(List<Feature> features,
                                           List<RequestResponseLog> logs,
                                           Path outputDir,
                                           String title,
                                           ReportOptions options) throws IOException {
        Map<String, String> diagramByTestId = new HashMap<>();
        for (PlantUmlForTest p : PlantUmlCreator.create(logs, options.arrowColors(), options.participantColors())) {
            if (!p.diagrams().isEmpty()) {
                diagramByTestId.put(p.testId(), p.diagrams().get(0)); // one per test (client-side splitting)
            }
        }
        return generateFromDiagrams(features, diagramByTestId, componentDiagram(logs), outputDir, title);
    }

    /** The run-level component diagram from all tracked logs, or {@code null} if nothing was tracked. */
    private static String componentDiagram(List<RequestResponseLog> logs) {
        List<ComponentRelationship> relationships = ComponentDiagramGenerator.extractRelationships(logs);
        return relationships.isEmpty() ? null : ComponentDiagramGenerator.generatePlantUml(relationships);
    }

    /** Renders from pre-computed diagrams — used by the merge path, where fragments already carry
     *  their PlantUML (no raw logs to re-render); no run-level component diagram in that path. */
    public static GeneratedReport generateFromDiagrams(List<Feature> features,
                                                       Map<String, String> diagramByTestId,
                                                       Path outputDir,
                                                       String title) throws IOException {
        return generateFromDiagrams(features, diagramByTestId, null, outputDir, title);
    }

    /** As {@link #generateFromDiagrams(List, Map, Path, String)}, with a run-level component diagram. */
    public static GeneratedReport generateFromDiagrams(List<Feature> features,
                                                       Map<String, String> diagramByTestId,
                                                       String componentDiagram,
                                                       Path outputDir,
                                                       String title) throws IOException {
        String html = renderHtml(features, diagramByTestId, componentDiagram, title);
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("TestRunReport.html");
        Files.writeString(file, html, StandardCharsets.UTF_8);
        return new GeneratedReport(html, file);
    }

    /** Builds the report HTML as a string (no IO) — the CLI writes it to its own {@code -o} path. */
    public static String renderHtml(List<Feature> features, Map<String, String> diagramByTestId, String title) {
        return renderHtml(features, diagramByTestId, null, title);
    }

    /** As {@link #renderHtml(List, Map, String)}, prepending a run-level component-diagram section. */
    public static String renderHtml(List<Feature> features, Map<String, String> diagramByTestId,
                                    String componentDiagram, String title) {
        StringBuilder body = new StringBuilder(2048);
        Map<String, String> diagramData = new LinkedHashMap<>();
        int diagramCounter = 0;
        int passed = 0;
        int failed = 0;
        int totalScenarios = 0;

        if (componentDiagram != null && !componentDiagram.isBlank()) {
            diagramData.put("puml-component", componentDiagram);
            body.append("  <section class=\"component\">").append(NL);
            body.append("    <h2>Component Diagram</h2>").append(NL);
            body.append("    <div class=\"plantuml-browser\" id=\"puml-component\"></div>").append(NL);
            body.append("    <details class=\"puml-src\"><summary>PlantUML source</summary><pre>")
                .append(HtmlEscaper.encode(componentDiagram)).append("</pre></details>").append(NL);
            body.append("  </section>").append(NL);
        }

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
                    diagramData.put(id, plantUml); // plain PlantUML; the renderer splits it to lines
                    body.append("      <div class=\"plantuml-browser\" id=\"").append(id)
                        .append("\"></div>").append(NL);
                    body.append("      <details class=\"puml-src\"><summary>PlantUML source</summary><pre>")
                        .append(HtmlEscaper.encode(plantUml)).append("</pre></details>").append(NL);
                }
                body.append("    </div>").append(NL);
            }
            body.append("  </section>").append(NL);
        }

        String summary = totalScenarios + " scenarios · " + passed + " passed · " + failed + " failed";
        return document(HtmlEscaper.encode(title), summary, diagramData, body.toString());
    }

    /** Default PlantUML-WASM CDN (matches the .NET {@code PlantUmlJsCdnBase}). */
    private static final String DEFAULT_CDN =
        "https://cdn.jsdelivr.net/gh/lemonlion/plantuml-js-plantuml_limit_size_98304@v1.2026.3beta6-patched";

    /** System property to point {@code viz-global.js}/{@code plantuml.js} at a local directory (or
     *  any base URL) instead of the CDN — set it to emit a self-contained, offline-renderable report
     *  (e.g. {@code -Dkronikol.report.assetBase=./assets}). Trailing slash is trimmed. */
    public static final String ASSET_BASE_PROPERTY = "kronikol.report.assetBase";

    private static final String RENDER_SCRIPT = loadResource("io/kronikol/report/kronikol-render.js");
    private static final String SEARCH_SCRIPT = loadResource("io/kronikol/report/advanced-search.js");

    /** The base for the WASM assets: the {@link #ASSET_BASE_PROPERTY} override, else the CDN. */
    static String assetBase() {
        String override = System.getProperty(ASSET_BASE_PROPERTY);
        if (override == null || override.isBlank()) {
            return DEFAULT_CDN;
        }
        String trimmed = override.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String document(String title, String summary, Map<String, String> diagramData, String body) {
        return "<!DOCTYPE html>" + NL
            + "<html lang=\"en\">" + NL
            + "<head>" + NL
            + "<meta charset=\"utf-8\">" + NL
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" + NL
            + "<title>" + title + "</title>" + NL
            + "<style>" + NL + STYLES + NL + "</style>" + NL
            + "<script defer src=\"" + assetBase() + "/viz-global.js\"></script>" + NL
            + "<script defer src=\"" + assetBase() + "/plantuml.js\"></script>" + NL
            + "<script id=\"kronikol-diagrams\" type=\"application/json\">" + Json.write(diagramData) + "</script>" + NL
            + "<script>" + NL + SEARCH_SCRIPT + NL + "</script>" + NL
            + "<script>" + NL + RENDER_SCRIPT + NL + "</script>" + NL
            + "</head>" + NL
            + "<body>" + NL
            + "<h1>" + title + "</h1>" + NL
            + "<div class=\"summary\">" + summary + "</div>" + NL
            + body
            + "</body>" + NL
            + "</html>" + NL;
    }

    private static String loadResource(String resource) {
        try (InputStream in = HtmlReportGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("bundled resource not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String STYLES = String.join(NL,
        "body { font-family: system-ui, sans-serif; margin: 2rem; color: #222; }",
        "h1 { font-size: 1.6rem; }",
        ".summary { color: #555; margin-bottom: 1.5rem; }",
        ".component { margin-bottom: 2rem; padding-bottom: 1rem; border-bottom: 2px solid #eee; }",
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
