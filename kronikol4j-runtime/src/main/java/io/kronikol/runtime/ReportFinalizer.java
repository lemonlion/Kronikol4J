package io.kronikol.runtime;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.HtmlReportGenerator.GeneratedReport;
import java.io.IOException;
import java.nio.file.Path;

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
        if (RunResults.isEmpty()) {
            return null;
        }
        return HtmlReportGenerator.generate(
            RunResults.toFeatures(), RequestResponseLogger.getAllLogs(), outputDir, title);
    }

    /** Generates the report to the configured/default output directory. */
    public static GeneratedReport finalizeRunToDefault(String title) throws IOException {
        return finalizeRun(resolveOutputDir(), title);
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
