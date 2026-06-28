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
 * @param generateComponentDiagram  embed the run-level component diagram (the .NET
 *                            {@code GenerateComponentDiagram}, default {@code true})
 * @param generateMergeableData  also write the enriched mergeable fragment ({@code TestRunReport.mergeable.json},
 *                            consumable by {@code kronikol merge}) for a standalone run (the .NET
 *                            {@code GenerateMergeableData}, default {@code false}; forked runs always emit a
 *                            fragment regardless)
 */
public record ReportControlOptions(String testRunReportTitle, String htmlReportFileName,
                                   boolean generateComponentDiagram, boolean generateMergeableData) {

    /** The .NET-default file name (without extension). */
    public static final String DEFAULT_HTML_FILE_NAME = "TestRunReport";

    /** The .NET defaults: no title override (caller's title used), {@code "TestRunReport"} file name,
     *  component diagram on, no standalone mergeable fragment. */
    public static final ReportControlOptions DEFAULTS =
        new ReportControlOptions(null, DEFAULT_HTML_FILE_NAME, true, false);

    public ReportControlOptions {
        htmlReportFileName = (htmlReportFileName == null || htmlReportFileName.isBlank())
            ? DEFAULT_HTML_FILE_NAME : htmlReportFileName;
    }

    /** Two-arg shape (component diagram on, no mergeable data) — the back-compatible constructor. */
    public ReportControlOptions(String testRunReportTitle, String htmlReportFileName) {
        this(testRunReportTitle, htmlReportFileName, true, false);
    }

    /** Three-arg shape (no mergeable data) — the back-compatible constructor. */
    public ReportControlOptions(String testRunReportTitle, String htmlReportFileName,
                                boolean generateComponentDiagram) {
        this(testRunReportTitle, htmlReportFileName, generateComponentDiagram, false);
    }

    public ReportControlOptions withTestRunReportTitle(String value) {
        return new ReportControlOptions(value, htmlReportFileName, generateComponentDiagram,
            generateMergeableData);
    }

    public ReportControlOptions withHtmlReportFileName(String value) {
        return new ReportControlOptions(testRunReportTitle, value, generateComponentDiagram,
            generateMergeableData);
    }

    public ReportControlOptions withGenerateComponentDiagram(boolean value) {
        return new ReportControlOptions(testRunReportTitle, htmlReportFileName, value, generateMergeableData);
    }

    public ReportControlOptions withGenerateMergeableData(boolean value) {
        return new ReportControlOptions(testRunReportTitle, htmlReportFileName, generateComponentDiagram, value);
    }

    /** The report title to use given a caller-supplied default: the override when set, else {@code fallback}. */
    public String resolveTitle(String fallback) {
        return testRunReportTitle != null && !testRunReportTitle.isBlank() ? testRunReportTitle : fallback;
    }
}
