package io.kronikol.report;

/**
 * Report-generation options controlling diagram rendering — currently the two colour modes that
 * {@link io.kronikol.diagram.plantuml.PlantUmlCreator} supports (mirroring .NET's
 * {@code sequenceDiagramArrowColors} / {@code sequenceDiagramParticipantColors}).
 *
 * <p>Immutable. Build from {@link #defaults()} with the {@code with…} methods, or read the runtime
 * toggles from system properties with {@link #fromSystemProperties()} (how the test-framework
 * listeners pick them up without an API change).
 */
public record ReportOptions(boolean arrowColors, boolean participantColors) {

    /** System property (boolean) enabling per-dependency-type arrow colours. */
    public static final String ARROW_COLORS_PROPERTY = "kronikol.diagram.arrowColors";
    /** System property (boolean) enabling per-participant colours. */
    public static final String PARTICIPANT_COLORS_PROPERTY = "kronikol.diagram.participantColors";

    /** The .NET defaults: arrows coloured per dependency type, participants uncoloured. */
    public static ReportOptions defaults() {
        return new ReportOptions(true, false);
    }

    public ReportOptions withArrowColors(boolean value) {
        return new ReportOptions(value, participantColors);
    }

    public ReportOptions withParticipantColors(boolean value) {
        return new ReportOptions(arrowColors, value);
    }

    /**
     * Reads {@link #ARROW_COLORS_PROPERTY} / {@link #PARTICIPANT_COLORS_PROPERTY} from system
     * properties, each falling back to the .NET {@link #defaults()} when unset — so a run keeps the
     * .NET look out of the box and can opt out with {@code -Dkronikol.diagram.arrowColors=false}.
     */
    public static ReportOptions fromSystemProperties() {
        ReportOptions defaults = defaults();
        return new ReportOptions(
            boolProperty(ARROW_COLORS_PROPERTY, defaults.arrowColors()),
            boolProperty(PARTICIPANT_COLORS_PROPERTY, defaults.participantColors()));
    }

    private static boolean boolProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }
}
