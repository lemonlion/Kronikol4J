package io.kronikol.core.context;

import io.kronikol.core.tracking.TestPhase;

/**
 * Holds the ambient {@link TestPhase} for the current thread.
 *
 * <p>The .NET version uses {@code AsyncLocal<TestPhase>} which auto-flows across {@code await};
 * Java has no automatic equivalent, so this is {@link ThreadLocal}-backed (plan §3.2). It covers
 * the common synchronous test-thread case; genuine async hand-offs use the propagation toolkit.
 *
 * <p><strong>Clearing is mandatory.</strong> Unlike .NET's auto-unwinding scope, a {@link ThreadLocal}
 * persists on a pooled thread until removed — call {@link #reset()} in test teardown to avoid leakage.
 */
public final class TestPhaseContext {

    private static final ThreadLocal<TestPhase> PHASE = ThreadLocal.withInitial(() -> TestPhase.UNKNOWN);

    private TestPhaseContext() {
    }

    public static TestPhase current() {
        return PHASE.get();
    }

    public static void set(TestPhase phase) {
        PHASE.set(phase == null ? TestPhase.UNKNOWN : phase);
    }

    public static void reset() {
        PHASE.remove();
    }
}
