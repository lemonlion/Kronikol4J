package io.kronikol.report;

import io.kronikol.report.data.ReportDataFormat;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Report-generation options: the two diagram colour modes (mirroring .NET's
 * {@code sequenceDiagramArrowColors} / {@code sequenceDiagramParticipantColors}) and the set of
 * machine-readable data formats to emit alongside the HTML report ({@code TestRunReport.json}/
 * {@code .xml}/{@code .yaml}).
 *
 * <p>Immutable. Build from {@link #defaults()} with the {@code with…} methods, or read the runtime
 * toggles from system properties with {@link #fromSystemProperties()} (how the test-framework
 * listeners pick them up without an API change).
 */
public record ReportOptions(boolean arrowColors, boolean participantColors,
                            Set<ReportDataFormat> dataFormats, boolean generateSchema) {

    /** System property (boolean) enabling per-dependency-type arrow colours. */
    public static final String ARROW_COLORS_PROPERTY = "kronikol.diagram.arrowColors";
    /** System property (boolean) enabling per-participant colours. */
    public static final String PARTICIPANT_COLORS_PROPERTY = "kronikol.diagram.participantColors";
    /** System property (comma-separated: {@code json,xml,yaml}) selecting report-data formats to emit. */
    public static final String DATA_FORMATS_PROPERTY = "kronikol.report.dataFormats";
    /** System property (boolean) enabling the {@code TestRunReport.schema.*} schema for each data format. */
    public static final String GENERATE_SCHEMA_PROPERTY = "kronikol.report.generateSchema";

    public ReportOptions {
        dataFormats = dataFormats == null
            ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(dataFormats));
    }

    /** Diagram colours only (no data files) — the back-compatible shape. */
    public ReportOptions(boolean arrowColors, boolean participantColors) {
        this(arrowColors, participantColors, Set.of(), false);
    }

    /** Colours + data formats, no schema — the back-compatible shape. */
    public ReportOptions(boolean arrowColors, boolean participantColors, Set<ReportDataFormat> dataFormats) {
        this(arrowColors, participantColors, dataFormats, false);
    }

    /** The .NET defaults: arrows coloured per dependency type, participants uncoloured, no data files. */
    public static ReportOptions defaults() {
        return new ReportOptions(true, false, Set.of(), false);
    }

    public ReportOptions withArrowColors(boolean value) {
        return new ReportOptions(value, participantColors, dataFormats, generateSchema);
    }

    public ReportOptions withParticipantColors(boolean value) {
        return new ReportOptions(arrowColors, value, dataFormats, generateSchema);
    }

    public ReportOptions withDataFormats(Set<ReportDataFormat> formats) {
        return new ReportOptions(arrowColors, participantColors, formats, generateSchema);
    }

    /** Enables the {@code TestRunReport.schema.json}/{@code .xsd} schema alongside each data format. */
    public ReportOptions withGenerateSchema(boolean value) {
        return new ReportOptions(arrowColors, participantColors, dataFormats, value);
    }

    /**
     * Reads the colour toggles ({@link #ARROW_COLORS_PROPERTY} / {@link #PARTICIPANT_COLORS_PROPERTY},
     * each falling back to the .NET {@link #defaults()}) and the {@link #DATA_FORMATS_PROPERTY} data
     * formats from system properties — so a run enables them with e.g.
     * {@code -Dkronikol.report.dataFormats=xml,yaml} and no code change.
     */
    public static ReportOptions fromSystemProperties() {
        ReportOptions defaults = defaults();
        return new ReportOptions(
            boolProperty(ARROW_COLORS_PROPERTY, defaults.arrowColors()),
            boolProperty(PARTICIPANT_COLORS_PROPERTY, defaults.participantColors()),
            parseDataFormats(System.getProperty(DATA_FORMATS_PROPERTY)),
            boolProperty(GENERATE_SCHEMA_PROPERTY, defaults.generateSchema()));
    }

    private static boolean boolProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
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
}
