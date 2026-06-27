package io.kronikol.core.tracking;

import io.kronikol.core.context.TestPhaseContext;
import java.util.function.Function;

/**
 * Attaches phase-specific {@link PhaseVariant}s to a {@link RequestResponseLog} when the test phase is
 * unknown at capture time and verbosity overrides are configured. Mirrors the .NET
 * {@code PhaseVariantExtensions} ({@code AttachVariants}/{@code WithVariants}).
 *
 * <p>The .NET original is a pair of generic extension methods on {@code RequestResponseLog}. Java has no
 * extension methods, so these are static helpers taking the log as the first argument; the fluent
 * {@link #withVariants} returns the log for inline chaining with {@code RequestResponseLogger.log(...)}.
 *
 * <p><strong>When variants are attached.</strong> Both conditions must hold (matching the source):
 * <ol>
 *   <li>the ambient {@link TestPhaseContext#current()} is {@link TestPhase#UNKNOWN} — i.e. the capturing
 *       adapter could not determine the phase, so the renderer must be given both forms to choose from; and
 *   <li>at least one of the setup/action verbosity overrides is non-null.
 * </ol>
 * When attached, each variant is built from its own override, falling back to {@code baseVerbosity} when
 * that override is null.
 */
public final class PhaseVariantExtensions {

    private PhaseVariantExtensions() {
    }

    /**
     * Computes and attaches {@link RequestResponseLog#setupVariant(PhaseVariant)} and
     * {@link RequestResponseLog#actionVariant(PhaseVariant)} when the current phase is
     * {@link TestPhase#UNKNOWN} and at least one verbosity override is set; otherwise a no-op.
     *
     * @param log             the log to attach variants to
     * @param baseVerbosity   the base (default) verbosity level
     * @param setupVerbosity  optional setup-phase verbosity override ({@code null} = use base)
     * @param actionVerbosity optional action-phase verbosity override ({@code null} = use base)
     * @param variantBuilder  builds a {@link PhaseVariant} for a given verbosity level; invoked once for
     *                        setup and once for action when the conditions are met
     * @param <V>             the verbosity type (e.g. {@link TrackingVerbosity})
     */
    public static <V> void attachVariants(
        RequestResponseLog log,
        V baseVerbosity,
        V setupVerbosity,
        V actionVerbosity,
        Function<V, PhaseVariant> variantBuilder) {

        if (TestPhaseContext.current() != TestPhase.UNKNOWN) {
            return;
        }
        if (setupVerbosity == null && actionVerbosity == null) {
            return;
        }

        log.setupVariant(variantBuilder.apply(setupVerbosity != null ? setupVerbosity : baseVerbosity));
        log.actionVariant(variantBuilder.apply(actionVerbosity != null ? actionVerbosity : baseVerbosity));
    }

    /**
     * Fluent form of {@link #attachVariants} that returns {@code log} for inline chaining.
     */
    public static <V> RequestResponseLog withVariants(
        RequestResponseLog log,
        V baseVerbosity,
        V setupVerbosity,
        V actionVerbosity,
        Function<V, PhaseVariant> variantBuilder) {

        attachVariants(log, baseVerbosity, setupVerbosity, actionVerbosity, variantBuilder);
        return log;
    }
}
