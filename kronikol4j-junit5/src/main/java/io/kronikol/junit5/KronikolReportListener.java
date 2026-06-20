package io.kronikol.junit5;

import io.kronikol.report.HtmlReportGenerator.GeneratedReport;
import io.kronikol.runtime.ReportFinalizer;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Finalizes the Kronikol4J report once per JVM, when the JUnit Platform launcher session closes
 * (the correct once-per-JVM granularity, plan §5.4 — preferred over {@code testPlanExecutionFinished}
 * which can fire multiple times). Registered via {@code ServiceLoader}
 * ({@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}).
 *
 * <p>Standalone (IDE / single JVM): self-finalizes to the output directory. Forked (build plugin
 * sets {@code kronikol.run.dir}): emits a fragment for the plugin to merge (Phase-5 path).
 */
public final class KronikolReportListener implements LauncherSessionListener {

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        try {
            GeneratedReport report = ReportFinalizer.finalizeRunToDefault("Kronikol4J Test Run");
            if (report != null) {
                System.out.println("[Kronikol4J] Report written: " + report.htmlFile().toAbsolutePath());
            }
        } catch (Exception e) {
            // Never fail the test run because of reporting.
            System.err.println("[Kronikol4J] report generation failed: " + e);
        }
    }
}
