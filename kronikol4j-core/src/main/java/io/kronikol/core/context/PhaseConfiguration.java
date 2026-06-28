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

    /**
     * Maps a BDD step-type string (e.g. {@code "Given"}, {@code "When"}, {@code "Then"}) to a {@link TestPhase}.
     * {@code Given}/{@code And}/{@code But} → {@link TestPhase#SETUP}; {@code When}/{@code Then} →
     * {@link TestPhase#ACTION}; anything else (incl. {@code null}) → {@link TestPhase#UNKNOWN}. Matching is a
     * case-insensitive prefix test (so {@code "Given that …"} still resolves), mirroring the .NET
     * {@code ResolvePhaseFromStepType}'s {@code StartsWith(OrdinalIgnoreCase)}.
     */
    public static TestPhase resolvePhaseFromStepType(String stepType) {
        if (stepType == null) {
            return TestPhase.UNKNOWN;
        }
        // Invariant casing (parity rule §6.5): uppercase via Locale.ROOT before prefix-matching.
        String s = stepType.toUpperCase(java.util.Locale.ROOT);
        if (s.startsWith("GIVEN") || s.startsWith("AND") || s.startsWith("BUT")) {
            return TestPhase.SETUP;
        }
        if (s.startsWith("WHEN") || s.startsWith("THEN")) {
            return TestPhase.ACTION;
        }
        return TestPhase.UNKNOWN;
    }
}
