package io.kronikol.core.tracking;

/**
 * The ambient test phase, used for verbosity/filtering decisions.
 *
 * <p>Mirrors the .NET {@code TestPhase} enum (with the same ordinals).
 */
public enum TestPhase {
    /** No phase detected — always tracks. */
    UNKNOWN,
    /** Given/And/But — pre-conditions. */
    SETUP,
    /** When/Then — actions and assertions. */
    ACTION
}
