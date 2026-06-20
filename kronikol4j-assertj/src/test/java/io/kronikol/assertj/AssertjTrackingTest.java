package io.kronikol.assertj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.RequestResponseLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AssertjTrackingTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
        KronikolAssertions.disableDescriptionTracking();
    }

    @Test
    void softAssertionFailuresAreRecordedAsRedNotes() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            var softly = new KronikolSoftAssertions();
            softly.assertThat(1).isEqualTo(2);     // collected failure -> recorded now
            softly.assertThat("a").isEqualTo("b"); // collected failure -> recorded now
            assertThatThrownBy(softly::assertAll).isInstanceOf(AssertionError.class);
        }

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(log ->
            assertThat(log.plantUml()).contains("✗").contains("#f8d7da"));
    }

    @Test
    void describedAssertionsAreRecordedViaTheDescriptionConsumer() {
        try (var scope = TestIdentityScope.begin("T", "t")) {
            KronikolAssertions.enableDescriptionTracking();
            assertThat(1).as("value is one").isEqualTo(1);
        }
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).anySatisfy(log ->
            assertThat(log.plantUml()).contains("value is one").contains("✓"));
    }
}
