package io.kronikol.report;

import io.kronikol.diagram.plantuml.DiagramOptions;
import io.kronikol.diagram.plantuml.FocusDeEmphasis;
import io.kronikol.diagram.plantuml.FocusEmphasis;
import io.kronikol.diagram.plantuml.GraphQlBodyFormat;
import io.kronikol.report.data.ReportDataFormat;
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
                            boolean generateSchema) {

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

    public ReportOptions {
        diagram = diagram == null ? DiagramOptions.defaults() : diagram;
        dataFormats = dataFormats == null
            ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(dataFormats));
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

    // --- withers ---
    public ReportOptions withDiagram(DiagramOptions value) {
        return new ReportOptions(value, dataFormats, generateSchema);
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
        return new ReportOptions(diagram, formats, generateSchema);
    }

    /** Enables the {@code TestRunReport.schema.json}/{@code .xsd} schema alongside each data format. */
    public ReportOptions withGenerateSchema(boolean value) {
        return new ReportOptions(diagram, dataFormats, value);
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
            boolProperty(GENERATE_SCHEMA_PROPERTY, false));
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
