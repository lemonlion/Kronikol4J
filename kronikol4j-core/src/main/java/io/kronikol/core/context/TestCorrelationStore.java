package io.kronikol.core.context;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Parallel-safe, data-keyed correlation of background work to tests (plan §3.2).
 *
 * <p>This is the mechanism that makes parallel tests + shared background infrastructure correct,
 * <em>without</em> relying on ambient flow: on every tracked write an extension records
 * {@code key -> test identity}; a background processor later resolves the key independently. It is
 * a concurrent map with a lazily-evicted TTL — the direct port of the .NET {@code TestCorrelationStore}.
 *
 * <p>The TTL is evaluated at {@link #resolve} time against each entry's creation instant (matching .NET),
 * so changing {@link #defaultTtl(Duration)} retroactively affects entries already stored.
 */
public final class TestCorrelationStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private record Entry(TestInfo info, Instant createdAt) {
    }

    private static final ConcurrentHashMap<String, Entry> STORE = new ConcurrentHashMap<>();

    // Seams for deterministic testing (plan §6.1).
    private static volatile Clock clock = Clock.systemUTC();
    private static volatile Duration ttl = DEFAULT_TTL;

    /** Optional diagnostic callback invoked when {@link #resolve} fails to find a live correlation. */
    private static volatile Consumer<String> onResolveMiss;

    private TestCorrelationStore() {
    }

    /** Records that work identified by {@code key} belongs to the given test (overwrites any existing). */
    public static void correlate(String key, String testName, String testId) {
        STORE.put(key, new Entry(new TestInfo(testName, testId), clock.instant()));
    }

    /**
     * Seeds a correlation for pre-existing data (items that exist before the test runs). Functionally
     * identical to {@link #correlate} but communicates intent — use it in setup for data not written by
     * the current test's tracked operations.
     */
    public static void seed(String key, String testName, String testId) {
        STORE.put(key, new Entry(new TestInfo(testName, testId), clock.instant()));
    }

    /** Resolves the test that owns {@code key}, or {@code null} if unknown/expired (fires onResolveMiss). */
    public static TestInfo resolve(String key) {
        Entry entry = STORE.get(key);
        if (entry == null) {
            notifyMiss(key);
            return null;
        }
        if (Duration.between(entry.createdAt(), clock.instant()).compareTo(ttl) > 0) {
            STORE.remove(key, entry);
            notifyMiss(key);
            return null;
        }
        return entry.info();
    }

    /** Removes a correlation entry by key. Returns {@code true} if one was present. */
    public static boolean remove(String key) {
        return STORE.remove(key) != null;
    }

    public static void clear() {
        STORE.clear();
    }

    /** The default time-to-live for correlation entries. Evaluated lazily at {@link #resolve} time. */
    public static Duration defaultTtl() {
        return ttl;
    }

    /** Sets the default TTL. Applies to existing entries too (TTL is evaluated against creation time). */
    public static void defaultTtl(Duration newTtl) {
        ttl = newTtl == null ? DEFAULT_TTL : newTtl;
    }

    /** Sets (or clears, with {@code null}) the callback invoked when {@link #resolve} misses. */
    public static void onResolveMiss(Consumer<String> callback) {
        onResolveMiss = callback;
    }

    private static void notifyMiss(String key) {
        Consumer<String> cb = onResolveMiss;
        if (cb != null) {
            cb.accept(key);
        }
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
        onResolveMiss = null;
    }
}
