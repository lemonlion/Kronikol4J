package io.kronikol.runtime;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.HtmlReportGenerator.GeneratedReport;
import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.merge.ReportFragment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

    /**
     * Forked mode (run-dir set): emits this JVM's fragment for the build plugin/CLI to merge and
     * returns {@code null}. Standalone: generates the HTML report to the output directory.
     */
    public static GeneratedReport finalizeRunToDefault(String title) throws IOException {
        if (isForkedMode()) {
            writeFragment(Path.of(System.getProperty(RUN_DIR_PROPERTY)), fragmentFileName(), title);
            return null;
        }
        return finalizeRun(resolveOutputDir(), title);
    }

    /**
     * Writes this JVM's report fragment to {@code runDir} atomically (temp + move), so a crashed
     * fork leaves a whole fragment or none (plan §5.3). Returns {@code null} if nothing was tracked.
     */
    public static Path writeFragment(Path runDir, String fileName, String title) throws IOException {
        if (RunResults.isEmpty()) {
            return null;
        }
        ReportFragment fragment = ReportFragments.fromRun(title);
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
