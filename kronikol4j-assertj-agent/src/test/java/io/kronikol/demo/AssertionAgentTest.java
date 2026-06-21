package io.kronikol.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.assertj.agent.KronikolAssertionAgent;
import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Lives in a "user" package ({@code io.kronikol.demo}) so the agent's source-expression lookup finds
 * this as the call site. Verifies Tier-2b: a PLAIN AssertJ assertion — no {@code Track} wrapper, no
 * {@code .as()} — has its actual + expected values AND its source expression captured automatically.
 */
class AssertionAgentTest {

    @BeforeAll
    static void installAgent() {
        KronikolAssertionAgent.install();
    }

    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void capturesActualExpectedAndExpressionFromAPlainAssertion() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            assertThat("egg").isEqualTo("egg");   // plain assertion — nothing Kronikol-specific
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(1);
        String note = logs.get(0).plantUml();
        assertThat(note).contains("✓");           // passed (green check)
        assertThat(note).contains("actual: egg");      // the captured actual value
        assertThat(note).contains("expected: egg");    // the captured expected value
        assertThat(note).contains("isEqualTo(\"egg\")"); // the source expression
    }

    @Test
    void capturesFailureWithActualAndExpected() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            try {
                assertThat("egg").isEqualTo("ham");
            } catch (AssertionError expected) {
                // we only care about the captured note
            }
        }

        String note = RequestResponseLogger.getAllLogs().get(0).plantUml();
        assertThat(note).contains("✗");           // failed (red cross)
        assertThat(note).contains("actual: egg");
        assertThat(note).contains("expected: ham");
    }

    @Test
    void capturesNoArgPredicateWithActualButNoExpected() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            assertThat("egg").isNotNull();          // no-arg predicate — there is no "expected"
        }

        String note = RequestResponseLogger.getAllLogs().get(0).plantUml();
        assertThat(note).contains("✓");
        assertThat(note).contains("actual: egg");
        assertThat(note).contains("isNotNull()");   // source expression of a no-arg call
        assertThat(note).doesNotContain("expected:"); // nothing to compare against
    }

    @Test
    void capturesNumericActualAndExpected() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            assertThat(40 + 2).isEqualTo(42);       // boxed int actual + expected
        }

        String note = RequestResponseLogger.getAllLogs().get(0).plantUml();
        assertThat(note).contains("actual: 42");
        assertThat(note).contains("expected: 42");
    }

    @Test
    void recordsNothingAndDoesNotThrowWhenNoTestIsInScope() {
        // No TestIdentityScope — Track has nothing to attribute the assertion to. The agent must
        // quietly no-op (never break a plain assertion that happens to run outside a tracked test).
        assertThat("egg").isEqualTo("egg");

        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
