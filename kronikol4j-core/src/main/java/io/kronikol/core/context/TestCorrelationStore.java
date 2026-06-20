package io.kronikol.core.context;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parallel-safe, data-keyed correlation of background work to tests (plan §3.2).
 *
 * <p>This is the mechanism that makes parallel tests + shared background infrastructure correct,
 * <em>without</em> relying on ambient flow: on every tracked write an extension records
 * {@code key -> test identity}; a background processor later resolves the key independently. It is
 * just a concurrent map with TTL — the direct, verbatim port of the .NET {@code TestCorrelationStore}.
 */
public final class TestCorrelationStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private record Entry(TestInfo info, Instant expiresAt) {
    }

    private static final ConcurrentHashMap<String, Entry> STORE = new ConcurrentHashMap<>();

    // Seams for deterministic testing (plan §6.1).
    private static volatile Clock clock = Clock.systemUTC();
    private static volatile Duration ttl = DEFAULT_TTL;

    private TestCorrelationStore() {
    }

    /** Records that work identified by {@code key} belongs to the given test. */
    public static void correlate(String key, String testName, String testId) {
        STORE.put(key, new Entry(new TestInfo(testName, testId), clock.instant().plus(ttl)));
    }

    /** Resolves the test that owns {@code key}, or {@code null} if unknown/expired. */
    public static TestInfo resolve(String key) {
        Entry entry = STORE.get(key);
        if (entry == null) {
            return null;
        }
        if (clock.instant().isAfter(entry.expiresAt())) {
            STORE.remove(key, entry);
            return null;
        }
        return entry.info();
    }

    public static void clear() {
        STORE.clear();
    }

    // --- test seams (package-private) ---
    static void setClock(Clock newClock) {
        clock = newClock;
    }

    static void setTtl(Duration newTtl) {
        ttl = newTtl;
    }

    static void resetSeams() {
        clock = Clock.systemUTC();
        ttl = DEFAULT_TTL;
    }
}
