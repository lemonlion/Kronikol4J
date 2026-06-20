package io.kronikol.core.tracking;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.support.SourceExpression;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

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

    private static void logAssertion(String description, boolean passed, String failureMessage) {
        TestInfo who = TestInfoResolver.resolve(null);
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
