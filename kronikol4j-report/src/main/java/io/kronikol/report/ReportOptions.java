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

    /** No colouring — the back-compatible default. */
    public static ReportOptions defaults() {
        return new ReportOptions(false, false);
    }

    public ReportOptions withArrowColors(boolean value) {
        return new ReportOptions(value, participantColors);
    }

    public ReportOptions withParticipantColors(boolean value) {
        return new ReportOptions(arrowColors, value);
    }

    /**
     * Reads {@link #ARROW_COLORS_PROPERTY} / {@link #PARTICIPANT_COLORS_PROPERTY} from system
     * properties (each defaults to {@code false}), so a run can enable colours with
     * {@code -Dkronikol.diagram.arrowColors=true} without any code change.
     */
    public static ReportOptions fromSystemProperties() {
        return new ReportOptions(
            boolProperty(ARROW_COLORS_PROPERTY), boolProperty(PARTICIPANT_COLORS_PROPERTY));
    }

    private static boolean boolProperty(String name) {
        return Boolean.parseBoolean(System.getProperty(name, "false"));
    }
}
