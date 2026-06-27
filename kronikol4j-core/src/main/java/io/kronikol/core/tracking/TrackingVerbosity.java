package io.kronikol.core.tracking;

/**
 * Shared verbosity scale controlling how much detail a tracking adapter records in a
 * {@link RequestResponseLog}.
 *
 * <p>This is the Java unification of the two structurally-identical .NET per-tracker enums
 * {@code MessageTrackerVerbosity} and {@code SqlTrackingVerbosityLevel}, both of which expose the
 * same three levels. Every .NET tracker resolves an <em>effective</em> level from a base value plus
 * optional setup/action overrides keyed on the current {@link TestPhase} — that resolution is the
 * generic {@link io.kronikol.core.context.PhaseConfiguration#effectiveVerbosity}, which composes with
 * this enum directly. Per-phase suppression is the sibling
 * {@link io.kronikol.core.context.PhaseConfiguration#shouldTrack}.
 *
 * <p><strong>Parity note.</strong> The .NET source defines exactly these three levels — no
 * {@code HeadersOnly} or {@code None} member exists anywhere in {@code c:\Code\Kronikol\src\Kronikol}.
 * The five-level scale floated in the parity roadmap is not backed by the behavioural spec, so it is
 * deliberately not modelled here (modelling unbacked levels would be a stub with no .NET behaviour to
 * mirror). Callers that need "don't track at all" use the phase toggles / {@code shouldTrack}, and
 * "headers only" is not a distinct .NET behaviour.
 */
public enum TrackingVerbosity {

    /** Full detail: raw payloads / SQL text + parameters and all headers, unclassified. */
    RAW,

    /** Classified operation labels with payloads still included. The default. */
    DETAILED,

    /** Minimal classified labels only — payloads / SQL text are omitted. */
    SUMMARISED;

    /**
     * The default level when none is configured, matching the .NET tracker defaults
     * (e.g. {@code MessageTrackerOptions.Verbosity = Detailed}).
     */
    public static final TrackingVerbosity DEFAULT = DETAILED;

    /**
     * Whether the recorded entry should carry the message payload / SQL text. Mirrors the single
     * distinction every .NET tracker makes: {@code Summarised} omits the payload; all other levels
     * include it.
     */
    public boolean includesPayload() {
        return this != SUMMARISED;
    }

    /**
     * Whether the entry should carry unclassified raw detail — full SQL text + parameters and all
     * headers, rather than a classified operation label. Only {@link #RAW} does.
     */
    public boolean includesRawDetail() {
        return this == RAW;
    }
}
