package io.kronikol.core.context;

import java.util.function.Supplier;

/**
 * Resolves the current test identity via the cascade (plan §3.2). Every tracking component calls
 * this to attribute an interaction to a test.
 *
 * <p>Order: (2) a framework-supplied delegate → (3) the {@link TestIdentityScope} scope → (4) the
 * global fallback. Layer 1 (HTTP headers) is folded into layer 3: the servlet filter opens a
 * {@link TestIdentityScope} scope for the request thread, so header-borne identity surfaces there.
 */
public final class TestInfoResolver {

    private TestInfoResolver() {
    }

    /**
     * @param delegateFetcher optional framework hook (e.g. JUnit's current test); may be {@code null}
     *                        and may throw if called off a test thread (caught and skipped).
     * @return the resolved identity, or {@code null} if no layer can supply one.
     */
    public static TestInfo resolve(Supplier<TestInfo> delegateFetcher) {
        // Layer 2 — delegate.
        if (delegateFetcher != null) {
            try {
                TestInfo result = delegateFetcher.get();
                if (result != null && !result.isUnknown()) {
                    return result;
                }
            } catch (RuntimeException ignored) {
                // Delegate threw (e.g. "no test context on this thread") — fall through.
            }
        }

        // Layer 3 — scope (also covers Layer 1: the servlet filter opens a scope here).
        TestInfo scoped = TestIdentityScope.current();
        if (scoped != null) {
            return scoped;
        }

        // Layer 4 — global fallback (serial-only).
        return TestIdentityScope.globalFallback();
    }
}
