package io.kronikol.core.context;

import io.kronikol.core.constants.TrackingHeaders;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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

    /**
     * Builds a {@link Supplier} that resolves test identity from HTTP request headers first, falling back
     * to {@code fallback} when the headers are absent — the .NET {@code CreateHttpFallbackFetcher} analog.
     * Eliminates the repetitive "headers-then-delegate" boilerplate when wiring a tracker's
     * {@code currentTestInfoFetcher}.
     *
     * <p>The {@link UnaryOperator} (header name → value, or {@code null}) replaces .NET's
     * {@code IHttpContextAccessor}, keeping {@code kronikol4j-core} free of any HTTP/servlet API: adapters
     * supply a lookup bound to their request (e.g. {@code request::getHeader}). Both
     * {@link TrackingHeaders#CURRENT_TEST_NAME} and {@link TrackingHeaders#CURRENT_TEST_ID} must be present;
     * a missing/throwing lookup falls through to {@code fallback} (matching .NET's swallow-and-delegate).
     *
     * @param headerLookup resolves a request-header value by name; may be {@code null} (treated as "no headers")
     * @param fallback     invoked when the headers are unavailable; must not be {@code null}
     */
    public static Supplier<TestInfo> createHttpFallbackFetcher(
            UnaryOperator<String> headerLookup, Supplier<TestInfo> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return () -> {
            TestInfo fromHeaders = resolveFromHeaders(headerLookup);
            return fromHeaders != null ? fromHeaders : fallback.get();
        };
    }

    /** Reads identity from the two tracking headers; {@code null} when absent or the lookup fails. */
    private static TestInfo resolveFromHeaders(UnaryOperator<String> headerLookup) {
        if (headerLookup == null) {
            return null;
        }
        try {
            String name = headerLookup.apply(TrackingHeaders.CURRENT_TEST_NAME);
            String id = headerLookup.apply(TrackingHeaders.CURRENT_TEST_ID);
            if (name != null && !name.isEmpty() && id != null && !id.isEmpty()) {
                return new TestInfo(name, id);
            }
        } catch (RuntimeException ignored) {
            // Header access can fail off a request thread — fall through to the delegate (matches .NET).
        }
        return null;
    }
}
