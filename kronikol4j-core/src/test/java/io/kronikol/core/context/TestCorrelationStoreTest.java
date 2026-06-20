package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestCorrelationStoreTest {

    @AfterEach
    void cleanup() {
        TestCorrelationStore.clear();
        TestCorrelationStore.resetSeams();
    }

    @Test
    void correlateThenResolveReturnsOwningTest() {
        TestCorrelationStore.correlate(CorrelationKeys.cosmos("Orders", "doc-1"), "TestA", "a");
        assertThat(TestCorrelationStore.resolve(CorrelationKeys.cosmos("Orders", "doc-1")))
            .isEqualTo(new TestInfo("TestA", "a"));
    }

    @Test
    void unknownKeyResolvesToNull() {
        assertThat(TestCorrelationStore.resolve("nope:none:x")).isNull();
    }

    @Test
    void parallelTestsAreKeyedIndependently() {
        TestCorrelationStore.correlate(CorrelationKeys.cosmos("DB", "X"), "TestA", "a");
        TestCorrelationStore.correlate(CorrelationKeys.cosmos("DB", "Y"), "TestB", "b");
        assertThat(TestCorrelationStore.resolve(CorrelationKeys.cosmos("DB", "X")).name()).isEqualTo("TestA");
        assertThat(TestCorrelationStore.resolve(CorrelationKeys.cosmos("DB", "Y")).name()).isEqualTo("TestB");
    }

    @Test
    void entriesExpireAfterTtl() {
        var base = Instant.parse("2026-01-01T00:00:00Z");
        var mutableClock = new ShiftableClock(base);
        TestCorrelationStore.setClock(mutableClock);
        TestCorrelationStore.setTtl(Duration.ofMinutes(30));

        TestCorrelationStore.correlate("k", "TestA", "a");
        assertThat(TestCorrelationStore.resolve("k")).isNotNull();

        mutableClock.shift(Duration.ofMinutes(31));
        assertThat(TestCorrelationStore.resolve("k")).isNull();
    }

    /** A clock whose instant can be advanced, for TTL testing. */
    private static final class ShiftableClock extends Clock {
        private Instant now;

        ShiftableClock(Instant start) {
            this.now = start;
        }

        void shift(Duration by) {
            now = now.plus(by);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }
}
