package io.kronikol.runtime;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.HtmlReportGenerator.GeneratedReport;
import io.kronikol.report.ReportOptions;
import io.kronikol.report.data.ReportData;
import io.kronikol.report.data.ReportDataFormat;
import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.merge.ReportFragment;
import io.kronikol.report.model.Feature;
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
        if (RunResults.isEmpty()) {
            return null;
        }
        List<Feature> features = RunResults.toFeatures();
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        GeneratedReport report = HtmlReportGenerator.generate(features, logs, outputDir, title, options);
        writeReportData(outputDir, features, logs, options);
        return report;
    }

    /** Emits {@code TestRunReport.<ext>} for each requested {@link ReportDataFormat} (none by default). */
    private static void writeReportData(Path outputDir, List<Feature> features,
                                        List<RequestResponseLog> logs, ReportOptions options) throws IOException {
        if (options.dataFormats().isEmpty()) {
            return;
        }
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
        for (ReportDataFormat format : options.dataFormats()) {
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
        return finalizeRun(resolveOutputDir(), title, options);
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
