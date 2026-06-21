package io.kronikol.report.flow;

/** The span-capture granularity (.NET {@code InternalFlowSpanGranularity}); shown in the empty-segment
 *  diagnostic message. {@link #toString()} matches the .NET enum name for that message. */
public enum InternalFlowSpanGranularity {
    AUTO_INSTRUMENTATION("AutoInstrumentation"),
    MANUAL("Manual"),
    FULL("Full");

    private final String displayName;

    InternalFlowSpanGranularity(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
