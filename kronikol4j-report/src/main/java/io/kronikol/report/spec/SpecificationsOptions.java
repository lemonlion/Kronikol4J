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

    /** System property (string) for the specifications title (default {@code "Service Specifications"}). */
    public static final String TITLE_PROPERTY = "kronikol.spec.title";
    /** System property (string) for the specifications HTML file name without extension. */
    public static final String HTML_FILE_NAME_PROPERTY = "kronikol.spec.htmlFileName";
    /** System property (string) for the specifications data file name without extension. */
    public static final String DATA_FILE_NAME_PROPERTY = "kronikol.spec.dataFileName";
    /** System property ({@code yaml}/{@code json}/{@code xml}) for the specifications data format. */
    public static final String DATA_FORMAT_PROPERTY = "kronikol.spec.dataFormat";
    /** System property (boolean) showing 1-based step numbers in the specifications HTML. */
    public static final String SHOW_STEP_NUMBERS_PROPERTY = "kronikol.spec.showStepNumbers";
    /** System property (boolean) emitting the specifications HTML report (default {@code true}). */
    public static final String GENERATE_REPORT_PROPERTY = "kronikol.spec.generateReport";
    /** System property (boolean) emitting the specifications data file (default {@code true}). */
    public static final String GENERATE_DATA_PROPERTY = "kronikol.spec.generateData";

    public static SpecificationsOptions defaults() {
        return new SpecificationsOptions("Service Specifications", "Specifications", "Specifications",
            ReportDataFormat.YAML, true, true, true, null);
    }

    /**
     * Reads the specifications options from system properties (each falling back to {@link #defaults()}),
     * so a listener-driven run configures them with e.g. {@code -Dkronikol.spec.generateReport=false} and no
     * code change. The custom stylesheet is not a system property (it stays the default).
     */
    public static SpecificationsOptions fromSystemProperties() {
        SpecificationsOptions d = defaults();
        return new SpecificationsOptions(
            stringProperty(TITLE_PROPERTY, d.title()),
            stringProperty(HTML_FILE_NAME_PROPERTY, d.htmlFileName()),
            stringProperty(DATA_FILE_NAME_PROPERTY, d.dataFileName()),
            ReportDataFormat.parse(System.getProperty(DATA_FORMAT_PROPERTY, "")).orElse(d.dataFormat()),
            boolProperty(SHOW_STEP_NUMBERS_PROPERTY, d.showStepNumbers()),
            boolProperty(GENERATE_REPORT_PROPERTY, d.generateReport()),
            boolProperty(GENERATE_DATA_PROPERTY, d.generateData()),
            d.customStyleSheet());
    }

    private static String stringProperty(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static boolean boolProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
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
