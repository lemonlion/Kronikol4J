package io.kronikol.spock;

import io.kronikol.core.tracking.TestPhase;
import java.util.Locale;

/**
 * Maps a Spock block label to a {@link TestPhase} (plan §3 phase tracking):
 * {@code given}/{@code setup}/{@code where} → Setup; {@code when}/{@code then}/{@code expect} → Action;
 * {@code and}/{@code cleanup}/{@code *} inherit the current phase. The Spock analog of the BDD
 * {@code GherkinPhase} mapping and the .NET phase inference.
 */
public final class SpockBlock {

    private SpockBlock() {
    }

    public static TestPhase forBlock(String blockLabel, TestPhase current) {
        String b = blockLabel == null ? "" : blockLabel.strip().toLowerCase(Locale.ROOT);
        return switch (b) {
            case "given", "setup", "where" -> TestPhase.SETUP; // pre-conditions / data tables
            case "when", "then", "expect" -> TestPhase.ACTION;  // stimulus + assertions
            default -> current;                                  // and / cleanup / * inherit
        };
    }
}
