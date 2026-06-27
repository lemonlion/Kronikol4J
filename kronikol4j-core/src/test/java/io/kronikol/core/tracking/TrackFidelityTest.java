package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kronikol.core.context.TestIdentityScope;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the Tier-4 assertion-fidelity additions to {@link Track}: value-returning {@code that}, the
 *  diagnostic log, the test-id resolver hook, and the {@code @SuppressAssertionTracking} marker. */
class TrackFidelityTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
        Track.clearDiagnosticLog();
        Track.diagnosticMode(false);
        Track.testIdResolver(null);
    }

    @Test
    void valueReturningThatReturnsTheValueAndRecordsANote() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            int value = Track.that("computes 42", () -> 42);
            assertThat(value).isEqualTo(42);
        }
        assertThat(RequestResponseLogger.getAllLogs().get(0).plantUml()).contains("✓ computes 42");
    }

    @Test
    void valueReturningThatRecordsRedNoteAndRethrows() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            assertThatThrownBy(() -> Track.that("bad", () -> {
                throw new AssertionError("nope");
            })).isInstanceOf(AssertionError.class).hasMessage("nope");
        }
        assertThat(RequestResponseLogger.getAllLogs().get(0).plantUml()).contains("✗ bad").contains("nope");
    }

    @Test
    void diagnosticLogRecordsOnlyWhenEnabled() {
        Track.recordDiagnostic("ignored — mode off");
        assertThat(Track.diagnosticLog()).isEmpty();

        Track.diagnosticMode(true);
        Track.recordDiagnostic("first");
        Track.recordDiagnostic("second");
        assertThat(Track.diagnosticLog()).containsExactly("first", "second");

        Track.clearDiagnosticLog();
        assertThat(Track.diagnosticLog()).isEmpty();
    }

    @Test
    void testIdResolverHookResolvesIdentityBeforeTheScope() {
        Track.testIdResolver(() -> "framework-id");
        // No TestIdentityScope active — the hook supplies the identity.
        Track.that("via hook", () -> { });

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).testId()).isEqualTo("framework-id");
        assertThat(logs.get(0).testName()).isEqualTo("framework-id"); // hook yields an id (name mirrors it)
    }

    @Test
    void testIdResolverThrowingFallsBackToScope() {
        Track.testIdResolver(() -> {
            throw new IllegalStateException("no scenario");
        });
        try (var scope = TestIdentityScope.begin("T", "t")) {
            Track.that("fallback", () -> { });
        }
        assertThat(RequestResponseLogger.getAllLogs().get(0).testId()).isEqualTo("t");
    }

    @Test
    void suppressAssertionTrackingIsRuntimeRetainedForMethodsAndTypes() {
        assertThat(SuppressAssertionTracking.class.isAnnotationPresent(java.lang.annotation.Retention.class))
            .isTrue();
        Target target = SuppressAssertionTracking.class.getAnnotation(Target.class);
        assertThat(target.value()).contains(ElementType.METHOD, ElementType.TYPE);
    }
}
