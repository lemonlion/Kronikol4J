package io.kronikol.report;

/**
 * Report-level control flags — the .NET {@code ReportConfigurationOptions} report-identity / output subset
 * (title, file name, component-diagram toggle, mergeable-data toggle, test-run-data toggle). Bundled (like
 * {@link io.kronikol.report.ci.CiPublishOptions} and {@link io.kronikol.report.model.HtmlCustomization}) so
 * the growing report-control surface has one home.
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
 * @param generateTestRunReportData  the master switch for the machine-readable test-run-report data file(s)
 *                            (the .NET {@code GenerateTestRunReportData}, default {@code true}). When on and
 *                            no explicit formats are configured, the single {@code testRunReportDataFormat}
 *                            (default JSON) is emitted; when off, no data file is written even if formats are
 *                            configured.
 * @param expectedTestCount  guard against partial runs: when set and the run produced fewer scenarios than
 *                            this, the Specifications report/data are suppressed (the .NET
 *                            {@code ExpectedTestCount}, default {@code null} = no guard)
 */
public record ReportControlOptions(String testRunReportTitle, String htmlReportFileName,
                                   boolean generateComponentDiagram, boolean generateMergeableData,
                                   boolean generateTestRunReportData, Integer expectedTestCount) {

    /** The .NET-default file name (without extension). */
    public static final String DEFAULT_HTML_FILE_NAME = "TestRunReport";

    /** The .NET defaults: no title override (caller's title used), {@code "TestRunReport"} file name,
     *  component diagram on, no standalone mergeable fragment, test-run data on, no expected-count guard. */
    public static final ReportControlOptions DEFAULTS =
        new ReportControlOptions(null, DEFAULT_HTML_FILE_NAME, true, false, true, null);

    public ReportControlOptions {
        htmlReportFileName = (htmlReportFileName == null || htmlReportFileName.isBlank())
            ? DEFAULT_HTML_FILE_NAME : htmlReportFileName;
    }

    /** Two-arg shape (component diagram on, no mergeable data, data on, no guard) — back-compatible. */
    public ReportControlOptions(String testRunReportTitle, String htmlReportFileName) {
        this(testRunReportTitle, htmlReportFileName, true, false, true, null);
    }

    /** Three-arg shape (no mergeable data, data on, no guard) — back-compatible. */
    public ReportControlOptions(String testRunReportTitle, String htmlReportFileName,
                                boolean generateComponentDiagram) {
        this(testRunReportTitle, htmlReportFileName, generateComponentDiagram, false, true, null);
    }

    /** Four-arg shape (data on, no guard) — back-compatible. */
    public ReportControlOptions(String testRunReportTitle, String htmlReportFileName,
                                boolean generateComponentDiagram, boolean generateMergeableData) {
        this(testRunReportTitle, htmlReportFileName, generateComponentDiagram, generateMergeableData, true,
            null);
    }

    /** Five-arg shape (no guard) — back-compatible. */
    public ReportControlOptions(String testRunReportTitle, String htmlReportFileName,
                                boolean generateComponentDiagram, boolean generateMergeableData,
                                boolean generateTestRunReportData) {
        this(testRunReportTitle, htmlReportFileName, generateComponentDiagram, generateMergeableData,
            generateTestRunReportData, null);
    }

    public ReportControlOptions withTestRunReportTitle(String value) {
        return new ReportControlOptions(value, htmlReportFileName, generateComponentDiagram,
            generateMergeableData, generateTestRunReportData, expectedTestCount);
    }

    public ReportControlOptions withHtmlReportFileName(String value) {
        return new ReportControlOptions(testRunReportTitle, value, generateComponentDiagram,
            generateMergeableData, generateTestRunReportData, expectedTestCount);
    }

    public ReportControlOptions withGenerateComponentDiagram(boolean value) {
        return new ReportControlOptions(testRunReportTitle, htmlReportFileName, value,
            generateMergeableData, generateTestRunReportData, expectedTestCount);
    }

    public ReportControlOptions withGenerateMergeableData(boolean value) {
        return new ReportControlOptions(testRunReportTitle, htmlReportFileName, generateComponentDiagram,
            value, generateTestRunReportData, expectedTestCount);
    }

    public ReportControlOptions withGenerateTestRunReportData(boolean value) {
        return new ReportControlOptions(testRunReportTitle, htmlReportFileName, generateComponentDiagram,
            generateMergeableData, value, expectedTestCount);
    }

    public ReportControlOptions withExpectedTestCount(Integer value) {
        return new ReportControlOptions(testRunReportTitle, htmlReportFileName, generateComponentDiagram,
            generateMergeableData, generateTestRunReportData, value);
    }

    /** The report title to use given a caller-supplied default: the override when set, else {@code fallback}. */
    public String resolveTitle(String fallback) {
        return testRunReportTitle != null && !testRunReportTitle.isBlank() ? testRunReportTitle : fallback;
    }

    /**
     * Whether the specifications report/data should be suppressed for a run that produced {@code scenarioCount}
     * scenarios — the .NET {@code ExpectedTestCount} guard: {@code true} when a count is set and the run fell
     * short (a partial run would yield misleading "living documentation").
     */
    public boolean shouldSuppressSpecifications(int scenarioCount) {
        return expectedTestCount != null && scenarioCount < expectedTestCount;
    }
}
