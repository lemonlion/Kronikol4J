package io.kronikol.steptracking.agent;

import io.kronikol.report.step.StepCollector;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * The step-tracking agent's runtime bridge — called by the inlined {@link StepAdvice} to open and close the
 * step around an annotated method via {@link StepCollector}. Kept off the advice hot path so the inlined code
 * stays tiny and the logic is unit-testable. Every entry point is exception-safe: a failure here must never
 * break the user's method.
 */
public final class StepAgentRecorder {

    private StepAgentRecorder() {
    }

    /** Opens the step for {@code method}: resolves keyword + text + parameters and calls {@code startStep}. */
    public static void enter(Method method, Object[] args) {
        try {
            StepText.StepInfo info = StepText.resolve(method);
            if (info == null) {
                return; // not actually a step method (matcher guarantees otherwise, but be defensive)
            }
            StepCollector.startStep(info.keyword(), info.text(), parameterNames(method), args);
        } catch (RuntimeException ignored) {
            // never let instrumentation break the instrumented method
        }
    }

    /**
     * Completes the step: failed (with the cause message) when the body threw; otherwise passed — or, when the
     * body returned a {@link CompletableFuture}, deferred via {@link StepCollector#completeStepAsync} so the
     * step finishes with the future (the returned wrapper replaces the method's return value).
     */
    public static Object exit(Object returned, Throwable thrown) {
        try {
            if (thrown != null) {
                StepCollector.completeStep(false, thrown.getMessage());
                return returned;
            }
            if (returned instanceof CompletableFuture<?> future) {
                return StepCollector.completeStepAsync(future);
            }
            StepCollector.completeStep(true, null);
        } catch (RuntimeException ignored) {
            // never let instrumentation break the instrumented method
        }
        return returned;
    }

    /** The method's parameter names, or {@code null} when they are not present (not compiled with -parameters). */
    private static String[] parameterNames(Method method) {
        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            return null;
        }
        String[] names = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            if (!params[i].isNamePresent()) {
                return null; // synthetic arg0/arg1 names carry no information — omit names entirely
            }
            names[i] = params[i].getName();
        }
        return names;
    }
}
