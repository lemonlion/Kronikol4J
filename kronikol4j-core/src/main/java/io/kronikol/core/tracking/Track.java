package io.kronikol.core.tracking;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.support.SourceExpression;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Assertion tracking — <strong>Tier 0</strong> (plan §3.9): a manual wrapper that records the outcome
 * of an assertion as a green/red note in the diagram. Java has no {@code CallerArgumentExpression},
 * so the description is explicit:
 *
 * <pre>{@code Track.that("order is confirmed", () -> assertThat(order.status()).isEqualTo(CONFIRMED));}</pre>
 *
 * <p>Tier 1 (the zero-weave AssertJ global-hook integration) and Tier 2 (compile-time full-fidelity
 * capture) build on this. A failing assertion is still rethrown.
 */
public final class Track {

    private static final URI ASSERT_URI = URI.create("assert://assertion/");

    private Track() {
    }

    /**
     * Records an assertion outcome directly as a note (no {@link Runnable} to run). Used by the
     * AssertJ Tier-1 integration's global hooks (plan §3.9).
     */
    public static void record(String description, boolean passed, String failureMessage) {
        logAssertion(description, passed, failureMessage);
    }

    /**
     * Runs {@code assertion}, recording it with an expression description <strong>auto-captured</strong>
     * from the call-site source (Tier 2, plan §3.9) — no explicit description needed:
     * <pre>{@code Track.that(() -> assertThat(order.status()).isEqualTo(CONFIRMED));}</pre>
     */
    public static void that(Runnable assertion) {
        String line = SourceExpression.forCallerOutside(
            Set.of(Track.class.getName(), SourceExpression.class.getName()));
        String description = SourceExpression.extractLambdaBody(line);
        if (description == null || description.isBlank()) {
            description = "assertion";
        }
        that(description, assertion);
    }

    /** Runs {@code assertion}, recording its pass/fail outcome (and message on failure) as a note. */
    public static void that(String description, Runnable assertion) {
        try {
            assertion.run();
        } catch (AssertionError failure) {
            logAssertion(description, false, failure.getMessage());
            throw failure;
        }
        logAssertion(description, true, null);
    }

    /**
     * As {@link #that(Runnable)}, but for a value-returning assertion: runs {@code assertion}, records it,
     * and returns its value (the .NET {@code Track.That<T>}). {@code thatAsync} is N/A in Java.
     */
    public static <T> T that(Supplier<T> assertion) {
        String line = SourceExpression.forCallerOutside(
            Set.of(Track.class.getName(), SourceExpression.class.getName()));
        String description = SourceExpression.extractLambdaBody(line);
        if (description == null || description.isBlank()) {
            description = "assertion";
        }
        return that(description, assertion);
    }

    /** As {@link #that(String, Runnable)}, but returns the assertion's value. */
    public static <T> T that(String description, Supplier<T> assertion) {
        T result;
        try {
            result = assertion.get();
        } catch (AssertionError failure) {
            logAssertion(description, false, failure.getMessage());
            throw failure;
        }
        logAssertion(description, true, null);
        return result;
    }

    // --- diagnostic log (assertion value-resolution fallbacks; rendered by the diagnostic report) ---

    private static volatile boolean diagnosticMode;
    private static final ConcurrentLinkedQueue<String> DIAGNOSTIC_ENTRIES = new ConcurrentLinkedQueue<>();

    /** Whether diagnostic entries are recorded for assertion value-resolution fallbacks. */
    public static boolean diagnosticMode() {
        return diagnosticMode;
    }

    /** Enables/disables diagnostic recording. */
    public static void diagnosticMode(boolean enabled) {
        diagnosticMode = enabled;
    }

    /** The recorded diagnostic log entries (a snapshot). */
    public static List<String> diagnosticLog() {
        return List.copyOf(DIAGNOSTIC_ENTRIES);
    }

    /** Clears all diagnostic log entries. */
    public static void clearDiagnosticLog() {
        DIAGNOSTIC_ENTRIES.clear();
    }

    /** Records a diagnostic entry (no-op unless {@link #diagnosticMode()} is on). Called by value resolution. */
    public static void recordDiagnostic(String entry) {
        if (diagnosticMode && entry != null) {
            DIAGNOSTIC_ENTRIES.add(entry);
        }
    }

    // --- test-id resolver hook (framework-context id, checked before the ambient scope) ---

    private static volatile Supplier<String> testIdResolver;

    /** The optional hook resolving the current test id from a framework context (or {@code null}). */
    public static Supplier<String> testIdResolver() {
        return testIdResolver;
    }

    /** Sets the hook that resolves the current test id from a framework context (checked before the scope). */
    public static void testIdResolver(Supplier<String> resolver) {
        testIdResolver = resolver;
    }

    /** Resolves the test identity for an assertion note: the {@link #testIdResolver} hook first (its id used
     *  as both name + id, matching .NET's override marker), then the ambient scope via {@link TestInfoResolver}. */
    private static TestInfo resolveWho() {
        Supplier<String> hook = testIdResolver;
        if (hook != null) {
            try {
                String id = hook.get();
                if (id != null && !id.isEmpty()) {
                    return new TestInfo(id, id);
                }
            } catch (RuntimeException ignored) {
                // resolver threw (e.g. no active scenario context) — fall through to the scope
            }
        }
        return TestInfoResolver.resolve(null);
    }

    private static void logAssertion(String description, boolean passed, String failureMessage) {
        TestInfo who = resolveWho();
        if (who == null) {
            return;
        }
        String note = passed
            ? "hnote across #d4edda\n✓ " + description + "\nend note"
            : "hnote across #f8d7da\n✗ " + description
                + (failureMessage == null ? "" : "\n" + failureMessage) + "\nend note";

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who)
            .method(Method.of("ASSERT"))
            .uri(ASSERT_URI)
            .serviceName("Assertion")
            .callerName(TrackingDefaults.CALLER_NAME)
            .type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID())
            .requestResponseId(UUID.randomUUID())
            .phase(TestPhaseContext.current())
            .build()
            .plantUml(note)); // rendered verbatim by PlantUmlCreator (override path)
    }
}
