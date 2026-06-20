package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kronikol.core.context.TestIdentityScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrackTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
    }

    @Test
    void passingAssertionRecordsAGreenNote() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            Track.that("order is confirmed", () -> {
                /* assertion passes */
            });
        }
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).plantUml())
            .contains("✓ order is confirmed")
            .contains("#d4edda");
    }

    @Test
    void failingAssertionRecordsARedNoteAndRethrows() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            assertThatThrownBy(() -> Track.that("value is 1",
                () -> {
                    throw new AssertionError("expected 1 but was 2");
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessage("expected 1 but was 2");
        }
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).plantUml())
            .contains("✗ value is 1")
            .contains("expected 1 but was 2")
            .contains("#f8d7da");
    }

    @Test
    void assertionOutsideTestContextIsNotRecorded() {
        Track.that("no test here", () -> {
        });
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
