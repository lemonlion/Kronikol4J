package io.kronikol.cucumber;

import io.kronikol.core.tracking.TestPhase;
import java.util.Locale;

/**
 * Maps a Gherkin step keyword to a {@link TestPhase} (plan §3 phase tracking):
 * {@code Given} → Setup; {@code When}/{@code Then} → Action; {@code And}/{@code But}/{@code *}
 * inherit the current phase. This is the BDD analog of the .NET phase inference.
 */
public final class GherkinPhase {

    private GherkinPhase() {
    }

    public static TestPhase forKeyword(String keyword, TestPhase current) {
        String k = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "given" -> TestPhase.SETUP;
            case "when", "then" -> TestPhase.ACTION;
            default -> current; // and / but / * (and localized variants) inherit
        };
    }
}
