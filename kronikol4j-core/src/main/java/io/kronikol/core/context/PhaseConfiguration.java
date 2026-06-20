package io.kronikol.core.context;

import io.kronikol.core.tracking.TestPhase;

/**
 * Phase-aware tracking decisions, driven by the ambient {@link TestPhaseContext}.
 * Mirrors the .NET {@code PhaseConfiguration}.
 */
public final class PhaseConfiguration {

    private PhaseConfiguration() {
    }

    /** Whether to track given the current phase and per-phase toggles (unknown phase always tracks). */
    public static boolean shouldTrack(boolean trackDuringSetup, boolean trackDuringAction) {
        return switch (TestPhaseContext.current()) {
            case SETUP -> trackDuringSetup;
            case ACTION -> trackDuringAction;
            case UNKNOWN -> true;
        };
    }

    /** The effective verbosity for the current phase, falling back to the default when unset. */
    public static <T> T effectiveVerbosity(T defaultVerbosity, T setupVerbosity, T actionVerbosity) {
        return switch (TestPhaseContext.current()) {
            case SETUP -> setupVerbosity != null ? setupVerbosity : defaultVerbosity;
            case ACTION -> actionVerbosity != null ? actionVerbosity : defaultVerbosity;
            case UNKNOWN -> defaultVerbosity;
        };
    }
}
