package io.kronikol.core.tracking;

/**
 * What a diagram marker record stands for. Values are appended, never renumbered, because they are
 * exported by name in the report data files.
 *
 * <p>Ports the .NET {@code DiagramMarkerKind}. Classifying at the source is exact; recovering the same
 * answer at the sink would mean pattern-matching PlantUML.
 */
public enum DiagramMarkerKind {

    /**
     * A fragment injected by the caller with no more specific classification. The default, so an
     * unclassified marker is never mistaken for a known one.
     */
    CUSTOM("Custom"),

    /** The bar that opens a Gherkin step. Already structured in the report's {@code steps}. */
    STEP("Step"),

    /** A tracked assertion's note. Already structured as an assertion sub-step. */
    ASSERTION("Assertion"),

    /** The band marking which row of a tabular input the following calls belong to. */
    ROW("Row"),

    /** The Setup/Action boundary. */
    PHASE("Phase");

    private final String displayName;

    DiagramMarkerKind(String displayName) {
        this.displayName = displayName;
    }

    /** The name used in the data files — the .NET enum member name, not the Java constant. */
    public String displayName() {
        return displayName;
    }
}
