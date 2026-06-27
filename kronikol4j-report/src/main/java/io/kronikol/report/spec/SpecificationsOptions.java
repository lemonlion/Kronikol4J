package io.kronikol.report.spec;

import io.kronikol.report.data.ReportDataFormat;

/**
 * Options for the Specifications report (the .NET {@code ReportConfigurationOptions} specifications subset).
 *
 * @param title              the report/data title (default {@code "Service Specifications"})
 * @param htmlFileName       the HTML file name without extension (default {@code "Specifications"})
 * @param dataFileName       the data file name without extension (default {@code "Specifications"})
 * @param dataFormat         the data format (default {@link ReportDataFormat#YAML})
 * @param showStepNumbers    show 1-based step numbers in the HTML (default {@code true})
 * @param generateReport     emit the HTML report (default {@code true})
 * @param generateData       emit the data file (default {@code true})
 * @param customStyleSheet   CSS appended into the report's main {@code <style>} (or {@code null})
 */
public record SpecificationsOptions(String title, String htmlFileName, String dataFileName,
                                    ReportDataFormat dataFormat, boolean showStepNumbers,
                                    boolean generateReport, boolean generateData, String customStyleSheet) {

    public static SpecificationsOptions defaults() {
        return new SpecificationsOptions("Service Specifications", "Specifications", "Specifications",
            ReportDataFormat.YAML, true, true, true, null);
    }

    public SpecificationsOptions withTitle(String v) {
        return new SpecificationsOptions(v, htmlFileName, dataFileName, dataFormat, showStepNumbers,
            generateReport, generateData, customStyleSheet);
    }

    public SpecificationsOptions withDataFormat(ReportDataFormat v) {
        return new SpecificationsOptions(title, htmlFileName, dataFileName, v, showStepNumbers,
            generateReport, generateData, customStyleSheet);
    }

    public SpecificationsOptions withShowStepNumbers(boolean v) {
        return new SpecificationsOptions(title, htmlFileName, dataFileName, dataFormat, v,
            generateReport, generateData, customStyleSheet);
    }

    public SpecificationsOptions withGenerateReport(boolean v) {
        return new SpecificationsOptions(title, htmlFileName, dataFileName, dataFormat, showStepNumbers,
            v, generateData, customStyleSheet);
    }

    public SpecificationsOptions withGenerateData(boolean v) {
        return new SpecificationsOptions(title, htmlFileName, dataFileName, dataFormat, showStepNumbers,
            generateReport, v, customStyleSheet);
    }

    public SpecificationsOptions withCustomStyleSheet(String v) {
        return new SpecificationsOptions(title, htmlFileName, dataFileName, dataFormat, showStepNumbers,
            generateReport, generateData, v);
    }
}
