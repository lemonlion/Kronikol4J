package io.kronikol.core.context;

/**
 * Ambient test-identity resolution layers 3 and 4 (plan §3.2).
 *
 * <ul>
 *   <li><b>Layer 3 (async scope):</b> a {@link ThreadLocal} set by {@link #begin}/{@link #setFromMessage}.
 *       Covers the synchronous test thread and any request/consumer thread a scope is opened on.
 *       (Layer 1 — HTTP headers — is realised by the servlet filter opening a scope here for the
 *       request thread, so it is picked up via this same layer.)</li>
 *   <li><b>Layer 4 (global fallback):</b> a single static value for pre-existing pool threads that
 *       carry no scope. <b>Not parallel-safe</b> (one value at a time) — use
 *       {@link TestCorrelationStore} for parallel-safe background attribution.</li>
 * </ul>
 *
 * <p><strong>Clearing is mandatory.</strong> .NET's {@code AsyncLocal}/{@code using} auto-unwinds;
 * a Java {@link ThreadLocal} does not. {@link #begin} returns an {@link AutoCloseable} for
 * try-with-resources that restores the previous value (or removes it); always use it.
 */
public final class TestIdentityScope {

    private static final ThreadLocal<TestInfo> CURRENT = new ThreadLocal<>();
    private static volatile TestInfo globalFallback;

    private TestIdentityScope() {
    }

    /** The current scoped identity, or {@code null}. */
    public static TestInfo current() {
        return CURRENT.get();
    }

    /** The global fallback identity, or {@code null}. */
    public static TestInfo globalFallback() {
        return globalFallback;
    }

    /**
     * Opens a scope for the current thread, returning an {@link AutoCloseable} that restores the
     * previous value on close. Use with try-with-resources.
     */
    public static Scope begin(String testName, String testId) {
        TestInfo previous = CURRENT.get();
        CURRENT.set(new TestInfo(testName, testId));
        return new Scope(previous);
    }

    /**
     * Sets the current identity without returning a scope — for message consumers that establish
     * identity for the duration of message handling (the consumer must clear it via {@link #clear()}
     * in a {@code finally} when done).
     */
    public static void setFromMessage(String testName, String testId) {
        CURRENT.set(new TestInfo(testName, testId));
    }

    /** Removes the current thread's scoped identity. */
    public static void clear() {
        CURRENT.remove();
    }

    public static void setGlobalFallback(String testName, String testId) {
        globalFallback = new TestInfo(testName, testId);
    }

    public static void clearGlobalFallback() {
        globalFallback = null;
    }

    /** A try-with-resources handle that restores the previous identity on {@link #close()}. */
    public static final class Scope implements AutoCloseable {
        private final TestInfo previous;

        private Scope(TestInfo previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
