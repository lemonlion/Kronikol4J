package io.kronikol.report;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.diagram.component.ComponentDiagramGenerator;
import io.kronikol.diagram.component.ComponentRelationship;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.report.data.ReportData;
import io.kronikol.report.flow.InternalFlowPopupInput;
import io.kronikol.report.flow.WholeTestFlowInput;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.HtmlCustomization;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the interactive HTML report (plan §1 Seam C / Phase 3). The output is a byte-for-byte
 * port of the .NET {@code ReportGenerator.GenerateHtmlReport} browser-rendering path, delegated to
 * {@link DotNetHtmlReportRenderer} and proven by {@code GoldenHtmlParityTest}.
 *
 * <p>This type keeps the public entry points stable: {@link #generate} re-renders each test's
 * PlantUML from the tracked logs (honouring the {@link ReportOptions} colour modes) plus a run-level
 * component diagram, while {@link #generateFromDiagrams}/{@link #renderHtml} take pre-computed
 * diagrams (the merge path). The version stamp comes from the jar manifest
 * ({@link ReportData#defaultKronikolVersion()}).
 */
public final class HtmlReportGenerator {

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

    /** As {@link #renderHtml(List, Map, String)}, embedding a run-level component-diagram section. */
    public static String renderHtml(List<Feature> features, Map<String, String> diagramByTestId,
                                    String componentDiagram, String title) {
        return postProcess(DotNetHtmlReportRenderer.render(
            features, diagramByTestId, componentDiagram, title, ReportData.defaultKronikolVersion()));
    }

    /**
     * As {@link #renderHtml(List, Map, String, String)}, additionally rendering the whole-test-flow
     * (internal-flow activity diagrams + flame charts) from the captured segments. This is the public
     * extension point for the whole-test-flow feature: a caller that collects finished OpenTelemetry
     * spans (via a {@code SpanProcessor}) builds the input with
     * {@link io.kronikol.report.flow.InternalFlowSegmentBuilder#buildWholeTestSegments} and a
     * {@link WholeTestFlowInput}, then passes it here.
     */
    public static String renderHtml(List<Feature> features, Map<String, String> diagramByTestId,
                                    String componentDiagram, String title, WholeTestFlowInput wholeTestFlow) {
        return postProcess(DotNetHtmlReportRenderer.render(
            features, diagramByTestId, componentDiagram, title, ReportData.defaultKronikolVersion(),
            false, Instant.EPOCH, Instant.EPOCH, HtmlCustomization.NONE, wholeTestFlow));
    }

    /**
     * As {@link #renderHtml(List, Map, String, String, WholeTestFlowInput)}, additionally emitting the
     * interactive internal-flow popup data ({@code window.__iflowConfig} + {@code window.__iflowSegments}).
     * A caller that collects finished spans builds the per-diagram segments with
     * {@link io.kronikol.report.flow.InternalFlowSegmentBuilder#buildSegments} and an
     * {@link InternalFlowPopupInput}.
     */
    public static String renderHtml(List<Feature> features, Map<String, String> diagramByTestId,
                                    String componentDiagram, String title, WholeTestFlowInput wholeTestFlow,
                                    InternalFlowPopupInput popup) {
        return postProcess(DotNetHtmlReportRenderer.render(
            features, diagramByTestId, componentDiagram, title, ReportData.defaultKronikolVersion(),
            false, Instant.EPOCH, Instant.EPOCH, HtmlCustomization.NONE, wholeTestFlow,
            io.kronikol.report.model.ParameterizedOptions.DEFAULTS, popup));
    }

    /** As {@link #generateFromDiagrams(List, Map, String, Path, String)}, with whole-test-flow views. */
    public static GeneratedReport generateFromDiagrams(List<Feature> features,
                                                       Map<String, String> diagramByTestId,
                                                       String componentDiagram, Path outputDir, String title,
                                                       WholeTestFlowInput wholeTestFlow) throws IOException {
        String html = renderHtml(features, diagramByTestId, componentDiagram, title, wholeTestFlow);
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("TestRunReport.html");
        Files.writeString(file, html, StandardCharsets.UTF_8);
        return new GeneratedReport(html, file);
    }

    /** Offline, self-contained report: rewrites the baked-in CDN base to the {@link #ASSET_BASE_PROPERTY}
     *  override, if set. */
    private static String postProcess(String html) {
        String base = assetBase();
        return base.equals(DEFAULT_CDN) ? html : html.replace(DEFAULT_CDN, base);
    }

    /** Default PlantUML-WASM CDN (matches the .NET {@code PlantUmlJsCdnBase}). */
    private static final String DEFAULT_CDN = DotNetHtmlReportRenderer.PLANTUML_CDN_BASE;

    /** System property to point {@code viz-global.js}/{@code plantuml.js} at a local directory (or
     *  any base URL) instead of the CDN — set it to emit a self-contained, offline-renderable report
     *  (e.g. {@code -Dkronikol.report.assetBase=./assets}). Trailing slash is trimmed. */
    public static final String ASSET_BASE_PROPERTY = "kronikol.report.assetBase";

    /** The base for the WASM assets: the {@link #ASSET_BASE_PROPERTY} override, else the CDN. */
    static String assetBase() {
        String override = System.getProperty(ASSET_BASE_PROPERTY);
        if (override == null || override.isBlank()) {
            return DEFAULT_CDN;
        }
        String trimmed = override.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
