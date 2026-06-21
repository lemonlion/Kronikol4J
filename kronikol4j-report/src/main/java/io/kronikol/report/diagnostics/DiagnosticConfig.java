package io.kronikol.report.diagnostics;

/**
 * The eight configuration values shown in the diagnostic report's "Configuration" table, as their
 * display strings (the .NET {@code DiagnosticReportGenerator} renders {@code options.X?.ToString()}).
 * Carrying display strings keeps the port byte-faithful without re-deriving .NET enum/bool formatting
 * (PascalCase enum names, {@code "True"}/{@code "False"}); {@link #dotNetDefaults()} is the baseline
 * those .NET defaults produce.
 */
public record DiagnosticConfig(
    String internalFlowTracking, String internalFlowSpanGranularity, String internalFlowActivitySources,
    String internalFlowDiagramStyle, String internalFlowNoDataBehavior, String diagramFormat,
    String plantUmlRendering, String generateComponentDiagram) {

    /** The display values produced by the .NET {@code ReportConfigurationOptions} defaults. */
    public static DiagnosticConfig dotNetDefaults() {
        return new DiagnosticConfig("True", "AutoInstrumentation", "<not configured>", "ActivityDiagram",
            "HideLink", "PlantUml", "BrowserJs", "True");
    }
}
