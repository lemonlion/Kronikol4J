package io.kronikol.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code kronikol { }} build extension. Surfaces the full {@code ReportOptions} configuration surface
 * (diagram colours/theme/setup-styling, report data formats + schema, note/header controls, the CI
 * summary/artifact options, and HTML customization) directly in the build script — the plugin forwards each
 * value the user sets to the matching {@code -Dkronikol.*} system property on every {@code Test} task, where
 * the forked JVM's {@code ReportOptions.fromSystemProperties()} reads it.
 *
 * <pre>
 * kronikol {
 *   reportDir = layout.buildDirectory.dir("reports/kronikol")
 *   title = "CI"
 *   arrowColors = true
 *   participantColors = true
 *   plantUmlTheme = "cerulean"
 *   dataFormats = listOf("json", "yaml")
 *   generateSchema = true
 *   writeCiSummary = true
 *   customCss = ".step { font-weight: bold }"
 * }
 * </pre>
 */
public interface KronikolExtension {

    /** Directory the merged HTML report is written to. */
    DirectoryProperty getReportDir();

    /** Report title. */
    Property<String> getTitle();

    // --- diagram styling ---

    /** Colour sequence-diagram arrows by dependency type. */
    Property<Boolean> getArrowColors();

    /** Colour participant headers by dependency type. */
    Property<Boolean> getParticipantColors();

    /** {@code !theme} name prepended to each diagram (e.g. {@code "cerulean"}). */
    Property<String> getPlantUmlTheme();

    /** Wrap setup-phase traces in a {@code partition … Setup} block. */
    Property<Boolean> getSeparateSetup();

    /** Colour the setup partition. */
    Property<Boolean> getHighlightSetup();

    /** Colour (e.g. {@code #F6F6F6}) for the setup partition. */
    Property<String> getSetupHighlightColor();

    /** Header keys excluded from diagram notes. */
    ListProperty<String> getExcludedHeaders();

    /** Drop all headers from diagram notes. */
    Property<Boolean> getExcludeAllHeaders();

    /** Focused-field emphasis styles ({@code BOLD}/{@code COLORED}). */
    ListProperty<String> getFocusEmphasis();

    /** Focused-field de-emphasis styles ({@code LIGHT_GRAY}/{@code SMALLER_TEXT}/{@code HIDDEN}). */
    ListProperty<String> getFocusDeEmphasis();

    /** GraphQL note body format ({@code JSON}/{@code FORMATTED_QUERY_ONLY}/{@code FORMATTED}/
     *  {@code FORMATTED_WITH_METADATA}). */
    Property<String> getGraphQlBodyFormat();

    /** Wrap each request label in a clickable internal-flow link. */
    Property<Boolean> getInternalFlowTracking();

    /** Cap note bodies at N lines (0 = off). */
    Property<Integer> getTruncateNotesAfterLines();

    /** Per-category colour overrides ({@code category -> #RRGGBB}). */
    MapProperty<String, String> getDependencyColors();

    /** Service-name → dependency-category overrides. */
    MapProperty<String, String> getServiceTypeOverrides();

    // --- report data ---

    /** Machine-readable report-data formats to emit ({@code json}/{@code xml}/{@code yaml}). */
    ListProperty<String> getDataFormats();

    /** Emit the {@code TestRunReport.schema.*} schema alongside each data format. */
    Property<Boolean> getGenerateSchema();

    // --- HTML customization ---

    /** Extra CSS injected into a trailing {@code <style>} block. */
    Property<String> getCustomCss();

    /** CSS appended into the main {@code <style>} block after the base stylesheet. */
    Property<String> getCustomStyleSheet();

    /** Base64 favicon {@code href} override. */
    Property<String> getCustomFaviconBase64();

    /** Logo HTML placed above the report {@code <h1>}. */
    Property<String> getCustomLogoHtml();

    /** Prefix each step with its 1-based number. */
    Property<Boolean> getShowStepNumbers();

    /** Emit a blank report when any scenario failed. */
    Property<Boolean> getGenerateBlankOnFailedTests();

    // --- CI summary / artifacts ---

    /** Write the markdown run summary to the detected CI platform. */
    Property<Boolean> getWriteCiSummary();

    /** Maximum diagrams in the CI summary. */
    Property<Integer> getMaxCiSummaryDiagrams();

    /** Publish report files as CI artifacts. */
    Property<Boolean> getPublishCiArtifacts();

    /** CI artifact name. */
    Property<String> getCiArtifactName();

    /** CI artifact retention in days. */
    Property<Integer> getCiArtifactRetentionDays();
}
