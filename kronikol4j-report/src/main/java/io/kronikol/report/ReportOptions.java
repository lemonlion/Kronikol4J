package io.kronikol.report;

import io.kronikol.diagram.plantuml.DiagramOptions;
import io.kronikol.diagram.plantuml.FocusDeEmphasis;
import io.kronikol.diagram.plantuml.FocusEmphasis;
import io.kronikol.diagram.plantuml.GraphQlBodyFormat;
import io.kronikol.report.ci.CiPublishOptions;
import io.kronikol.report.data.ReportDataFormat;
import io.kronikol.report.model.HtmlCustomization;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Report-generation options: the full diagram-styling surface (held as a {@link DiagramOptions}) plus the
 * report-level toggles — the machine-readable {@link ReportDataFormat data formats} to emit and the
 * {@code TestRunReport.schema.*} switch.
 *
 * <p>The diagram options ({@code arrowColors}/{@code participantColors}/{@code plantUmlTheme}/
 * {@code excludedHeaders}/{@code separateSetup}/{@code highlightSetup}/{@code setupHighlightColor}/
 * {@code focusEmphasis}/{@code focusDeEmphasis}) are reachable both as first-class {@code with…} methods
 * and via {@link #diagram()}/{@link #withDiagram(DiagramOptions)}. (Caller-supplied note content
 * processors are not part of {@code ReportOptions} — pass them programmatically to
 * {@code PlantUmlCreator.create(logs, DiagramOptions, NoteProcessors)}.)
 *
 * <p>Immutable. Build from {@link #defaults()} with the {@code with…} methods, or read the runtime
 * toggles from system properties with {@link #fromSystemProperties()} (how the test-framework listeners
 * pick them up without an API change).
 */
public record ReportOptions(DiagramOptions diagram, Set<ReportDataFormat> dataFormats,
                            boolean generateSchema, HtmlCustomization customization, CiPublishOptions ci,
                            boolean diagnosticMode) {

    /** System property (boolean) enabling per-dependency-type arrow colours. */
    public static final String ARROW_COLORS_PROPERTY = "kronikol.diagram.arrowColors";
    /** System property (boolean) enabling per-participant colours. */
    public static final String PARTICIPANT_COLORS_PROPERTY = "kronikol.diagram.participantColors";
    /** System property (comma-separated: {@code json,xml,yaml}) selecting report-data formats to emit. */
    public static final String DATA_FORMATS_PROPERTY = "kronikol.report.dataFormats";
    /** System property (boolean) enabling the {@code TestRunReport.schema.*} schema for each data format. */
    public static final String GENERATE_SCHEMA_PROPERTY = "kronikol.report.generateSchema";
    /** System property ({@code !theme} name) prepended to each sequence diagram. */
    public static final String PLANTUML_THEME_PROPERTY = "kronikol.diagram.plantUmlTheme";
    /** System property (boolean) wrapping setup-phase traces in a {@code partition … Setup} block. */
    public static final String SEPARATE_SETUP_PROPERTY = "kronikol.diagram.separateSetup";
    /** System property (boolean) colouring the setup partition (default true). */
    public static final String HIGHLIGHT_SETUP_PROPERTY = "kronikol.diagram.highlightSetup";
    /** System property (colour, e.g. {@code #F6F6F6}) for the setup partition. */
    public static final String SETUP_HIGHLIGHT_COLOR_PROPERTY = "kronikol.diagram.setupHighlightColor";
    /** System property (comma-separated header keys) excluded from diagram notes. */
    public static final String EXCLUDED_HEADERS_PROPERTY = "kronikol.diagram.excludedHeaders";
    /** System property (comma-separated {@code BOLD}/{@code COLORED}) for focused-field emphasis. */
    public static final String FOCUS_EMPHASIS_PROPERTY = "kronikol.diagram.focusEmphasis";
    /** System property (comma-separated {@code LIGHT_GRAY}/{@code SMALLER_TEXT}/{@code HIDDEN}). */
    public static final String FOCUS_DE_EMPHASIS_PROPERTY = "kronikol.diagram.focusDeEmphasis";
    /** System property (one of {@code JSON}/{@code FORMATTED_QUERY_ONLY}/{@code FORMATTED}/
     *  {@code FORMATTED_WITH_METADATA}) selecting how GraphQL request bodies render in notes. */
    public static final String GRAPHQL_BODY_FORMAT_PROPERTY = "kronikol.diagram.graphQlBodyFormat";
    /** System property (boolean) wrapping each request label in a clickable {@code [[#iflow-…]]} link. */
    public static final String INTERNAL_FLOW_TRACKING_PROPERTY = "kronikol.diagram.internalFlowTracking";
    /** System property (int) capping note bodies at N lines (the rest replaced by {@code ...}); 0 = off. */
    public static final String TRUNCATE_NOTES_PROPERTY = "kronikol.diagram.truncateNotesAfterLines";
    /** System property (boolean) dropping all headers from diagram notes. */
    public static final String EXCLUDE_ALL_HEADERS_PROPERTY = "kronikol.diagram.excludeAllHeaders";
    /** System property (comma-separated {@code category=#RRGGBB}) overriding per-category arrow/participant
     *  colours. */
    public static final String DEPENDENCY_COLORS_PROPERTY = "kronikol.diagram.dependencyColors";
    /** System property (comma-separated {@code ServiceName=category}) overriding a service's/caller's
     *  detected dependency category (which drives its shape + colour). */
    public static final String SERVICE_TYPE_OVERRIDES_PROPERTY = "kronikol.diagram.serviceTypeOverrides";
    /** System property (CSS) injected into a trailing {@code <style>} block of the report. */
    public static final String CUSTOM_CSS_PROPERTY = "kronikol.report.customCss";
    /** System property (CSS) appended into the main {@code <style>} block after the base stylesheet. */
    public static final String CUSTOM_STYLESHEET_PROPERTY = "kronikol.report.customStyleSheet";
    /** System property (base64) overriding the report favicon {@code href}. */
    public static final String CUSTOM_FAVICON_PROPERTY = "kronikol.report.customFaviconBase64";
    /** System property (HTML) for a logo placed above the report {@code <h1>}. */
    public static final String CUSTOM_LOGO_PROPERTY = "kronikol.report.customLogoHtml";
    /** System property (boolean) prefixing each step with its 1-based number. */
    public static final String SHOW_STEP_NUMBERS_PROPERTY = "kronikol.report.showStepNumbers";
    /** System property (boolean) emitting a blank report when any scenario failed. */
    public static final String BLANK_ON_FAILED_PROPERTY = "kronikol.report.generateBlankOnFailedTests";
    /** System property (boolean) writing the standalone {@code DiagnosticReport.html} at end-of-run. */
    public static final String DIAGNOSTIC_MODE_PROPERTY = "kronikol.report.diagnosticMode";
    /** System property (boolean) writing the markdown run summary to the detected CI platform. */
    public static final String WRITE_CI_SUMMARY_PROPERTY = "kronikol.ci.writeCiSummary";
    /** System property (int) capping diagrams in the CI summary (default 10). */
    public static final String MAX_CI_SUMMARY_DIAGRAMS_PROPERTY = "kronikol.ci.maxCiSummaryDiagrams";
    /** System property (boolean) publishing report files as CI artifacts. */
    public static final String PUBLISH_CI_ARTIFACTS_PROPERTY = "kronikol.ci.publishCiArtifacts";
    /** System property (string) naming the CI artifact (default {@code "TestReports"}). */
    public static final String CI_ARTIFACT_NAME_PROPERTY = "kronikol.ci.ciArtifactName";
    /** System property (int) CI artifact retention in days (default 1). */
    public static final String CI_ARTIFACT_RETENTION_DAYS_PROPERTY = "kronikol.ci.ciArtifactRetentionDays";

    public ReportOptions {
        diagram = diagram == null ? DiagramOptions.defaults() : diagram;
        dataFormats = dataFormats == null
            ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(dataFormats));
        customization = customization == null ? HtmlCustomization.NONE : customization;
        ci = ci == null ? CiPublishOptions.NONE : ci;
    }

    /** Three-arg shape (no HTML customization, no CI publishing) — the back-compatible constructor. */
    public ReportOptions(DiagramOptions diagram, Set<ReportDataFormat> dataFormats, boolean generateSchema) {
        this(diagram, dataFormats, generateSchema, HtmlCustomization.NONE);
    }

    /** Four-arg shape (no CI publishing) — the back-compatible constructor. */
    public ReportOptions(DiagramOptions diagram, Set<ReportDataFormat> dataFormats, boolean generateSchema,
                         HtmlCustomization customization) {
        this(diagram, dataFormats, generateSchema, customization, CiPublishOptions.NONE);
    }

    /** Five-arg shape (diagnostic mode off) — the back-compatible constructor. */
    public ReportOptions(DiagramOptions diagram, Set<ReportDataFormat> dataFormats, boolean generateSchema,
                         HtmlCustomization customization, CiPublishOptions ci) {
        this(diagram, dataFormats, generateSchema, customization, ci, false);
    }

    /** Diagram colours only (no data files) — the back-compatible shape. */
    public ReportOptions(boolean arrowColors, boolean participantColors) {
        this(DiagramOptions.colours(arrowColors, participantColors), Set.of(), false);
    }

    /** Colours + data formats, no schema — the back-compatible shape. */
    public ReportOptions(boolean arrowColors, boolean participantColors, Set<ReportDataFormat> dataFormats) {
        this(DiagramOptions.colours(arrowColors, participantColors), dataFormats, false);
    }

    /** Colours + data formats + schema — the back-compatible shape. */
    public ReportOptions(boolean arrowColors, boolean participantColors, Set<ReportDataFormat> dataFormats,
                         boolean generateSchema) {
        this(DiagramOptions.colours(arrowColors, participantColors), dataFormats, generateSchema);
    }

    /** The .NET defaults: arrows coloured per dependency type, participants uncoloured, no data files. */
    public static ReportOptions defaults() {
        return new ReportOptions(DiagramOptions.defaults(), Set.of(), false);
    }

    // --- back-compat / convenience accessors delegating to the diagram options ---
    public boolean arrowColors() {
        return diagram.arrowColors();
    }

    public boolean participantColors() {
        return diagram.participantColors();
    }

    public String plantUmlTheme() {
        return diagram.plantUmlTheme();
    }

    public GraphQlBodyFormat graphQlBodyFormat() {
        return diagram.graphQlBodyFormat();
    }

    public boolean internalFlowTracking() {
        return diagram.internalFlowTracking();
    }

    public int truncateNotesAfterLines() {
        return diagram.truncateNotesAfterLines();
    }

    public boolean excludeAllHeaders() {
        return diagram.excludeAllHeaders();
    }

    public Map<String, String> dependencyColors() {
        return diagram.dependencyColors();
    }

    public Map<String, String> serviceTypeOverrides() {
        return diagram.serviceTypeOverrides();
    }

    /**
     * The .NET {@code TestRunReportDataFormat} scalar view of {@link #dataFormats()}: the single format the
     * test-run-report data file is emitted in. Java generalizes .NET's one-format option to a set (emit
     * several files); this accessor reports the first configured format in insertion order, or the .NET
     * default ({@link ReportDataFormat#JSON}) when none is configured. {@code dataFormats()} remains the
     * source of truth and the superset API.
     */
    public ReportDataFormat testRunReportDataFormat() {
        return dataFormats.isEmpty() ? ReportDataFormat.JSON : dataFormats.iterator().next();
    }

    // --- withers ---
    public ReportOptions withDiagram(DiagramOptions value) {
        return new ReportOptions(value, dataFormats, generateSchema, customization, ci, diagnosticMode);
    }

    public ReportOptions withArrowColors(boolean value) {
        return withDiagram(diagram.withArrowColors(value));
    }

    public ReportOptions withParticipantColors(boolean value) {
        return withDiagram(diagram.withParticipantColors(value));
    }

    public ReportOptions withPlantUmlTheme(String theme) {
        return withDiagram(diagram.withPlantUmlTheme(theme));
    }

    public ReportOptions withExcludedHeaders(List<String> headers) {
        return withDiagram(diagram.withExcludedHeaders(headers));
    }

    public ReportOptions withSeparateSetup(boolean value) {
        return withDiagram(diagram.withSeparateSetup(value));
    }

    public ReportOptions withHighlightSetup(boolean value) {
        return withDiagram(diagram.withHighlightSetup(value));
    }

    public ReportOptions withSetupHighlightColor(String color) {
        return withDiagram(diagram.withSetupHighlightColor(color));
    }

    public ReportOptions withFocusEmphasis(Set<FocusEmphasis> value) {
        return withDiagram(diagram.withFocusEmphasis(value));
    }

    public ReportOptions withFocusDeEmphasis(Set<FocusDeEmphasis> value) {
        return withDiagram(diagram.withFocusDeEmphasis(value));
    }

    public ReportOptions withGraphQlBodyFormat(GraphQlBodyFormat format) {
        return withDiagram(diagram.withGraphQlBodyFormat(format));
    }

    public ReportOptions withInternalFlowTracking(boolean value) {
        return withDiagram(diagram.withInternalFlowTracking(value));
    }

    public ReportOptions withTruncateNotesAfterLines(int value) {
        return withDiagram(diagram.withTruncateNotesAfterLines(value));
    }

    public ReportOptions withExcludeAllHeaders(boolean value) {
        return withDiagram(diagram.withExcludeAllHeaders(value));
    }

    public ReportOptions withDependencyColors(Map<String, String> value) {
        return withDiagram(diagram.withDependencyColors(value));
    }

    public ReportOptions withServiceTypeOverrides(Map<String, String> value) {
        return withDiagram(diagram.withServiceTypeOverrides(value));
    }

    public ReportOptions withDataFormats(Set<ReportDataFormat> formats) {
        return new ReportOptions(diagram, formats, generateSchema, customization, ci, diagnosticMode);
    }

    /**
     * Sets the single test-run-report data format — the .NET {@code TestRunReportDataFormat} convenience over
     * {@link #withDataFormats(Set)} (so {@code TestRunReport.<ext>} is emitted in exactly that format). Pass
     * {@code null} to emit no data file. To emit several formats at once, use {@link #withDataFormats(Set)}.
     */
    public ReportOptions withTestRunReportDataFormat(ReportDataFormat format) {
        return withDataFormats(format == null ? Set.of() : Set.of(format));
    }

    /** Enables the {@code TestRunReport.schema.json}/{@code .xsd} schema alongside each data format. */
    public ReportOptions withGenerateSchema(boolean value) {
        return new ReportOptions(diagram, dataFormats, value, customization, ci, diagnosticMode);
    }

    /** The HTML customization (CSS/favicon/logo/step-numbers) applied to the generated report. */
    public ReportOptions withHtmlCustomization(HtmlCustomization value) {
        return new ReportOptions(diagram, dataFormats, generateSchema,
            value == null ? HtmlCustomization.NONE : value, ci, diagnosticMode);
    }

    /** The CI summary/artifact-publishing options applied at end-of-run. */
    public ReportOptions withCi(CiPublishOptions value) {
        return new ReportOptions(diagram, dataFormats, generateSchema, customization,
            value == null ? CiPublishOptions.NONE : value, diagnosticMode);
    }

    /**
     * Enables diagnostic mode — the standalone {@code DiagnosticReport.html} (tracking health, warnings,
     * statistics) is written at end-of-run, including when logs were recorded but no test contexts were
     * enqueued (the empty-report case). Mirrors .NET {@code ReportConfigurationOptions.DiagnosticMode}.
     */
    public ReportOptions withDiagnosticMode(boolean value) {
        return new ReportOptions(diagram, dataFormats, generateSchema, customization, ci, value);
    }

    /**
     * Reads every diagram + report toggle from system properties (each falling back to {@link #defaults()}),
     * so a listener-driven run configures them with e.g. {@code -Dkronikol.diagram.separateSetup=true} or
     * {@code -Dkronikol.report.dataFormats=xml,yaml} and no code change.
     */
    public static ReportOptions fromSystemProperties() {
        DiagramOptions d = DiagramOptions.defaults();
        DiagramOptions diagram = new DiagramOptions(
            boolProperty(ARROW_COLORS_PROPERTY, d.arrowColors()),
            boolProperty(PARTICIPANT_COLORS_PROPERTY, d.participantColors()),
            stringProperty(PLANTUML_THEME_PROPERTY, d.plantUmlTheme()),
            parseList(System.getProperty(EXCLUDED_HEADERS_PROPERTY), d.excludedHeaders()),
            boolProperty(SEPARATE_SETUP_PROPERTY, d.separateSetup()),
            boolProperty(HIGHLIGHT_SETUP_PROPERTY, d.highlightSetup()),
            stringProperty(SETUP_HIGHLIGHT_COLOR_PROPERTY, d.setupHighlightColor()),
            parseEnumSet(System.getProperty(FOCUS_EMPHASIS_PROPERTY), FocusEmphasis.class, d.focusEmphasis()),
            parseEnumSet(System.getProperty(FOCUS_DE_EMPHASIS_PROPERTY), FocusDeEmphasis.class,
                d.focusDeEmphasis()),
            parseGraphQlBodyFormat(System.getProperty(GRAPHQL_BODY_FORMAT_PROPERTY), d.graphQlBodyFormat()),
            boolProperty(INTERNAL_FLOW_TRACKING_PROPERTY, d.internalFlowTracking()),
            intProperty(TRUNCATE_NOTES_PROPERTY, d.truncateNotesAfterLines()),
            boolProperty(EXCLUDE_ALL_HEADERS_PROPERTY, d.excludeAllHeaders()),
            parseMap(System.getProperty(DEPENDENCY_COLORS_PROPERTY), d.dependencyColors()),
            parseMap(System.getProperty(SERVICE_TYPE_OVERRIDES_PROPERTY), d.serviceTypeOverrides()));
        return new ReportOptions(diagram,
            parseDataFormats(System.getProperty(DATA_FORMATS_PROPERTY)),
            boolProperty(GENERATE_SCHEMA_PROPERTY, false),
            customizationFromSystemProperties(),
            ciFromSystemProperties(),
            boolProperty(DIAGNOSTIC_MODE_PROPERTY, false));
    }

    /** Builds the {@link CiPublishOptions} from system properties (all defaulting to the .NET defaults). */
    public static CiPublishOptions ciFromSystemProperties() {
        return new CiPublishOptions(
            boolProperty(WRITE_CI_SUMMARY_PROPERTY, false),
            intProperty(MAX_CI_SUMMARY_DIAGRAMS_PROPERTY, 10),
            boolProperty(PUBLISH_CI_ARTIFACTS_PROPERTY, false),
            stringProperty(CI_ARTIFACT_NAME_PROPERTY, CiPublishOptions.NONE.ciArtifactName()),
            intProperty(CI_ARTIFACT_RETENTION_DAYS_PROPERTY, CiPublishOptions.NONE.ciArtifactRetentionDays()));
    }

    /**
     * Builds the {@link HtmlCustomization} from system properties (CI metadata is supplied by the
     * CI/merge path, not a system property, so it stays {@code null} here). Returns {@link
     * HtmlCustomization#NONE} when no customization property is set.
     */
    public static HtmlCustomization customizationFromSystemProperties() {
        String customCss = stringProperty(CUSTOM_CSS_PROPERTY, null);
        String customStyleSheet = stringProperty(CUSTOM_STYLESHEET_PROPERTY, null);
        String favicon = stringProperty(CUSTOM_FAVICON_PROPERTY, null);
        String logo = stringProperty(CUSTOM_LOGO_PROPERTY, null);
        boolean showStepNumbers = boolProperty(SHOW_STEP_NUMBERS_PROPERTY, false);
        boolean blankOnFailed = boolProperty(BLANK_ON_FAILED_PROPERTY, false);
        if (customCss == null && customStyleSheet == null && favicon == null && logo == null
            && !showStepNumbers && !blankOnFailed) {
            return HtmlCustomization.NONE;
        }
        return new HtmlCustomization(null, customCss, favicon, logo, showStepNumbers, blankOnFailed,
            customStyleSheet);
    }

    private static String stringProperty(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static boolean boolProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static int intProperty(String name, int fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Parses {@code key=value,key=value} into an ordered map (the fallback if blank/empty). */
    private static Map<String, String> parseMap(String value, Map<String, String> fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String token : value.split(",")) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue; // skip malformed entries (no key)
            }
            String key = token.substring(0, eq).strip();
            String val = token.substring(eq + 1).strip();
            if (!key.isEmpty()) {
                map.put(key, val);
            }
        }
        return map.isEmpty() ? fallback : map;
    }

    private static GraphQlBodyFormat parseGraphQlBodyFormat(String value, GraphQlBodyFormat fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return GraphQlBodyFormat.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback; // unknown token — keep the default (matches the lenient enum-set parsing)
        }
    }

    private static Set<ReportDataFormat> parseDataFormats(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<ReportDataFormat> formats = new LinkedHashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split(",")) {
            ReportDataFormat.parse(token).ifPresent(formats::add);
        }
        return formats;
    }

    private static List<String> parseList(String value, List<String> fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        List<String> list = new ArrayList<>();
        for (String token : value.split(",")) {
            String t = token.strip();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        return list;
    }

    private static <E extends Enum<E>> Set<E> parseEnumSet(String value, Class<E> type, Set<E> fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        EnumSet<E> set = EnumSet.noneOf(type);
        for (String token : value.split(",")) {
            String t = token.strip().toUpperCase(Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            try {
                set.add(Enum.valueOf(type, t));
            } catch (IllegalArgumentException ignored) {
                // unknown token — skip (matches the lenient data-format parsing)
            }
        }
        return set;
    }
}
