package io.kronikol.runtime;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.HtmlReportGenerator.GeneratedReport;
import io.kronikol.report.ReportOptions;
import io.kronikol.report.diagnostics.DiagnosticConfig;
import io.kronikol.report.diagnostics.DiagnosticReportGenerator;
import io.kronikol.report.ci.CiArtifactPublisher;
import io.kronikol.report.ci.CiDiagram;
import io.kronikol.report.ci.CiEnvironment;
import io.kronikol.report.ci.CiPublishOptions;
import io.kronikol.report.ci.CiSummaryGenerator;
import io.kronikol.report.ci.CiSummaryWriter;
import io.kronikol.report.data.ReportData;
import io.kronikol.report.data.ReportDataFormat;
import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.merge.ReportFragment;
import io.kronikol.report.model.Feature;
import io.kronikol.report.spec.SpecificationsOptions;
import io.kronikol.report.spec.SpecificationsReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the report at end-of-run from the collected {@link RunResults} and the accumulated
 * {@link RequestResponseLogger} logs.
 *
 * <p>Mode detection (plan §5.3): when {@link #RUN_DIR_PROPERTY} is set by the build plugin, this JVM
 * is a fork and should emit a fragment for the plugin to merge; otherwise (IDE / single JVM) it
 * self-finalizes to the output directory. The fragment-emission + cross-fork merge path is the
 * Phase-5 build-plugin work; this MVP implements standalone self-finalization.
 */
public final class ReportFinalizer {

    /** Output directory for the standalone report. */
    public static final String OUTPUT_DIR_PROPERTY = "kronikol.output.dir";
    /** Set by the build plugin on forked JVMs (presence => fragment mode, §5.3). */
    public static final String RUN_DIR_PROPERTY = "kronikol.run.dir";

    private static final String DEFAULT_OUTPUT = "build/kronikol-report";

    private ReportFinalizer() {
    }

    /** Generates the report to {@code outputDir}; returns {@code null} if nothing was tracked. */
    public static GeneratedReport finalizeRun(Path outputDir, String title) throws IOException {
        return finalizeRun(outputDir, title, ReportOptions.defaults());
    }

    /** As {@link #finalizeRun(Path, String)}, honouring the diagram colour {@code options} and emitting
     *  the requested machine-readable {@code TestRunReport.<ext>} data files alongside the HTML. */
    public static GeneratedReport finalizeRun(Path outputDir, String title, ReportOptions options)
            throws IOException {
        return finalizeRun(outputDir, title, options, SpecificationsOptions.defaults());
    }

    /**
     * As {@link #finalizeRun(Path, String, ReportOptions)}, additionally emitting the Specifications report
     * ({@code Specifications.html} + {@code Specifications.<ext>}) per {@code specs} — the .NET
     * {@code GenerateSpecificationsReport}/{@code GenerateSpecificationsData} path. Defaults emit both;
     * disable via {@link SpecificationsOptions#withGenerateReport}/{@code withGenerateData}.
     */
    public static GeneratedReport finalizeRun(Path outputDir, String title, ReportOptions options,
                                              SpecificationsOptions specs) throws IOException {
        if (RunResults.isEmpty()) {
            // .NET parity: when logs were recorded but no test contexts were enqueued, the main report would
            // be empty — emit the diagnostic report (when enabled) to explain why, then skip the empty report.
            if (options.diagnosticMode()) {
                List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
                if (!logs.isEmpty()) {
                    writeDiagnosticReport(outputDir, List.of(), logs, options);
                }
            }
            return null;
        }
        List<Feature> features = RunResults.toFeatures();
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        GeneratedReport report = HtmlReportGenerator.generate(features, logs, outputDir, title, options);
        writeReportData(outputDir, features, logs, options);
        writeCiOutputs(outputDir, features, logs, options);
        if (options.diagnosticMode()) {
            writeDiagnosticReport(outputDir, features, logs, options);
        }
        if (options.generateMergeableData()) {
            // .NET GenerateMergeableData: emit the enriched mergeable fragment for a standalone run so it can
            // later be combined via `kronikol merge` (forked runs already emit a fragment to the run dir).
            ReportFragment fragment = ReportFragments.fromRun(options.control().resolveTitle(title), options);
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("TestRunReport.mergeable.json"),
                FragmentJson.toJson(fragment), StandardCharsets.UTF_8);
        }
        if (specs != null && (specs.generateReport() || specs.generateData())) {
            // .NET GenerateSpecificationsReport/Data: the separate "living documentation" Specifications.html
            // + Specifications.<ext>, re-rendering the same scenarios/diagrams with spec styling.
            SpecificationsReport.write(outputDir, features, diagramByTestId(logs, options), specs);
        }
        return report;
    }

    /** The per-test diagram map (one diagram per test, client-side splitting) for the specifications report. */
    private static Map<String, String> diagramByTestId(List<RequestResponseLog> logs, ReportOptions options) {
        Map<String, String> diagrams = new LinkedHashMap<>();
        for (PlantUmlForTest p : PlantUmlCreator.create(logs, options.diagram())) {
            if (!p.diagrams().isEmpty()) {
                diagrams.put(p.testId(), p.diagrams().get(0));
            }
        }
        return diagrams;
    }

    /**
     * Writes the standalone {@code DiagnosticReport.html} (the .NET {@code DiagnosticReportGenerator.Generate}
     * file-emit step). The "Configuration" section reflects the one toggle {@link ReportOptions} currently
     * carries ({@code internalFlowTracking}); the remaining config rows track the .NET defaults until their
     * owning options land (the not-yet-built report-control flags).
     */
    private static void writeDiagnosticReport(Path outputDir, List<Feature> features,
                                              List<RequestResponseLog> logs, ReportOptions options)
            throws IOException {
        String html = DiagnosticReportGenerator.buildHtml(logs, features, diagnosticConfig(options));
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("DiagnosticReport.html"), html, StandardCharsets.UTF_8);
    }

    /** The diagnostic "Configuration" dump: actual {@code internalFlowTracking}, .NET defaults for the rest. */
    private static DiagnosticConfig diagnosticConfig(ReportOptions options) {
        DiagnosticConfig d = DiagnosticConfig.dotNetDefaults();
        String internalFlow = options.internalFlowTracking() ? "True" : "False";
        return new DiagnosticConfig(internalFlow, d.internalFlowSpanGranularity(),
            d.internalFlowActivitySources(), d.internalFlowDiagramStyle(), d.internalFlowNoDataBehavior(),
            d.diagramFormat(), d.plantUmlRendering(), d.generateComponentDiagram());
    }

    /**
     * Writes the CI summary (to {@code CiSummary.md} + the detected CI platform's summary channel) and
     * publishes the report files as CI artifacts, when enabled via {@link ReportOptions#ci()}. Mirrors the
     * .NET {@code ReportGenerator} CI-summary / artifact-publish blocks.
     */
    private static void writeCiOutputs(Path outputDir, List<Feature> features,
                                       List<RequestResponseLog> logs, ReportOptions options) throws IOException {
        CiPublishOptions ci = options.ci();
        if (ci.writeCiSummary()) {
            List<CiDiagram> diagrams = buildCiDiagrams(logs, options);
            String markdown = CiSummaryGenerator.generateMarkdown(features, diagrams, diagrams,
                RunResults.startedAt(), Instant.now(), ci.maxCiSummaryDiagrams(),
                CiSummaryGenerator.DEFAULT_PLANTUML_SERVER);
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("CiSummary.md"), markdown, StandardCharsets.UTF_8);
            CiSummaryWriter.write(markdown, CiEnvironment.detect());
        }
        if (ci.publishCiArtifacts()) {
            List<String> reportFiles = collectReportFiles(outputDir);
            if (!reportFiles.isEmpty()) {
                CiArtifactPublisher.publish(reportFiles, CiEnvironment.detect(), ci.ciArtifactName(),
                    ci.ciArtifactRetentionDays());
            }
        }
    }

    /** Builds the flat {@link CiDiagram} list (one entry per diagram part) from the run's tracked logs. */
    private static List<CiDiagram> buildCiDiagrams(List<RequestResponseLog> logs, ReportOptions options) {
        List<CiDiagram> out = new ArrayList<>();
        for (PlantUmlForTest p : PlantUmlCreator.create(logs, options.diagram())) {
            for (String diagram : p.diagrams()) {
                out.add(new CiDiagram(p.testId(), diagram));
            }
        }
        return out;
    }

    /** The generated report files eligible for CI artifact publishing (matches the .NET extension filter). */
    private static List<String> collectReportFiles(Path outputDir) throws IOException {
        if (!Files.isDirectory(outputDir)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> entries = Files.list(outputDir)) {
            entries.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (name.endsWith(".html") || name.endsWith(".yaml") || name.endsWith(".yml")
                    || name.endsWith(".md") || name.endsWith(".json") || name.endsWith(".xml")) {
                    files.add(p.toString());
                }
            });
        }
        java.util.Collections.sort(files); // deterministic order
        return files;
    }

    /**
     * Emits the machine-readable test-run-report data file(s). Mirrors .NET: gated by the
     * {@code generateTestRunReportData} master switch (default on); the formats are the explicit
     * {@code dataFormats} set when configured, else the single {@code testRunReportDataFormat} (default JSON,
     * the .NET {@code TestRunReportDataFormat}) — so a default run emits {@code TestRunReport.json}.
     */
    private static void writeReportData(Path outputDir, List<Feature> features,
                                        List<RequestResponseLog> logs, ReportOptions options) throws IOException {
        if (!options.generateTestRunReportData()) {
            return;
        }
        java.util.Set<ReportDataFormat> formats = options.dataFormats().isEmpty()
            ? java.util.Set.of(options.testRunReportDataFormat()) : options.dataFormats();
        Map<String, List<String>> diagrams = new LinkedHashMap<>();
        for (PlantUmlForTest p : PlantUmlCreator.create(logs, options.diagram())) {
            if (!p.diagrams().isEmpty()) {
                diagrams.put(p.testId(), p.diagrams());
            }
        }
        Map<String, List<RequestResponseLog>> logsByTestId = new LinkedHashMap<>();
        for (RequestResponseLog log : logs) {
            logsByTestId.computeIfAbsent(log.testId(), k -> new ArrayList<>()).add(log);
        }
        ReportData data = new ReportData(ReportData.defaultKronikolVersion(),
            RunResults.startedAt(), Instant.now(), features, diagrams, logsByTestId);
        Files.createDirectories(outputDir);
        for (ReportDataFormat format : formats) {
            Files.writeString(outputDir.resolve("TestRunReport." + format.extension()),
                format.serialize(data), StandardCharsets.UTF_8);
            if (options.generateSchema()) {
                Files.writeString(outputDir.resolve("TestRunReport.schema." + schemaExtension(format)),
                    schemaContent(format), StandardCharsets.UTF_8);
            }
        }
    }

    /** The schema file extension for a data format (.NET {@code GetSchemaExtension}): XML &rarr; XSD,
     *  JSON/YAML &rarr; JSON Schema. */
    private static String schemaExtension(ReportDataFormat format) {
        return format == ReportDataFormat.XML ? "xsd" : "json";
    }

    private static String schemaContent(ReportDataFormat format) {
        return format == ReportDataFormat.XML
            ? io.kronikol.report.data.ReportDataSchema.xmlSchema()
            : io.kronikol.report.data.ReportDataSchema.jsonSchema();
    }

    /**
     * Forked mode (run-dir set): emits this JVM's fragment for the build plugin/CLI to merge and
     * returns {@code null}. Standalone: generates the HTML report to the output directory. Diagram
     * colour options are read from system properties ({@link ReportOptions#fromSystemProperties()}),
     * so a run enables them with {@code -Dkronikol.diagram.arrowColors=true} and friends.
     */
    public static GeneratedReport finalizeRunToDefault(String title) throws IOException {
        return finalizeRunToDefault(title, ReportOptions.fromSystemProperties());
    }

    /** As {@link #finalizeRunToDefault(String)}, with explicit colour {@code options}. */
    public static GeneratedReport finalizeRunToDefault(String title, ReportOptions options)
            throws IOException {
        if (isForkedMode()) {
            writeFragment(Path.of(System.getProperty(RUN_DIR_PROPERTY)), fragmentFileName(), title, options);
            return null;
        }
        return finalizeRun(resolveOutputDir(), title, options, SpecificationsOptions.fromSystemProperties());
    }

    /** As {@link #writeFragment(Path, String, String, ReportOptions)} with default colour options. */
    public static Path writeFragment(Path runDir, String fileName, String title) throws IOException {
        return writeFragment(runDir, fileName, title, ReportOptions.defaults());
    }

    /**
     * Writes this JVM's report fragment to {@code runDir} atomically (temp + move), so a crashed
     * fork leaves a whole fragment or none (plan §5.3). Returns {@code null} if nothing was tracked.
     */
    public static Path writeFragment(Path runDir, String fileName, String title, ReportOptions options)
            throws IOException {
        if (RunResults.isEmpty()) {
            return null;
        }
        ReportFragment fragment = ReportFragments.fromRun(title, options);
        Files.createDirectories(runDir);
        Path target = runDir.resolve(fileName);
        Path temp = runDir.resolve(fileName + ".tmp");
        Files.writeString(temp, FragmentJson.toJson(fragment), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static String fragmentFileName() {
        return "fragment-" + ProcessHandle.current().pid() + ".json";
    }

    static Path resolveOutputDir() {
        String configured = System.getProperty(OUTPUT_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(DEFAULT_OUTPUT);
    }

    /** Whether this JVM is a build-orchestrated fork (should emit a fragment, not self-finalize). */
    public static boolean isForkedMode() {
        String runDir = System.getProperty(RUN_DIR_PROPERTY);
        return runDir != null && !runDir.isBlank();
    }
}
