package io.kronikol.report;

/**
 * Report-level control flags — the .NET {@code ReportConfigurationOptions} report-identity subset:
 * the test-run-report title and the HTML file name. Bundled (like {@link io.kronikol.report.ci.CiPublishOptions}
 * and {@link io.kronikol.report.model.HtmlCustomization}) so the growing report-control surface has one home.
 *
 * @param testRunReportTitle  overrides the report title; {@code null} → the title the caller passes to the
 *                            finalizer is used (the .NET {@code TestRunReportTitle}, default {@code null} =
 *                            auto-derived — the auto-derivation from {@code ComponentDiagramOptions.Title} /
 *                            {@code FixedNameForReceivingService} depends on options not yet ported)
 * @param htmlReportFileName  the HTML report file name <em>without</em> extension (the .NET
 *                            {@code HtmlTestRunReportFileName}, default {@code "TestRunReport"} →
 *                            {@code TestRunReport.html})
 */
public record ReportControlOptions(String testRunReportTitle, String htmlReportFileName) {

    /** The .NET-default file name (without extension). */
    public static final String DEFAULT_HTML_FILE_NAME = "TestRunReport";

    /** The .NET defaults: no title override (caller's title used), {@code "TestRunReport"} file name. */
    public static final ReportControlOptions DEFAULTS = new ReportControlOptions(null, DEFAULT_HTML_FILE_NAME);

    public ReportControlOptions {
        htmlReportFileName = (htmlReportFileName == null || htmlReportFileName.isBlank())
            ? DEFAULT_HTML_FILE_NAME : htmlReportFileName;
    }

    public ReportControlOptions withTestRunReportTitle(String value) {
        return new ReportControlOptions(value, htmlReportFileName);
    }

    public ReportControlOptions withHtmlReportFileName(String value) {
        return new ReportControlOptions(testRunReportTitle, value);
    }

    /** The report title to use given a caller-supplied default: the override when set, else {@code fallback}. */
    public String resolveTitle(String fallback) {
        return testRunReportTitle != null && !testRunReportTitle.isBlank() ? testRunReportTitle : fallback;
    }
}
