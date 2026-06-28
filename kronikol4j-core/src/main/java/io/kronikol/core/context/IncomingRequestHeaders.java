package io.kronikol.core.context;

import java.util.function.Function;

/**
 * Ambient access to the headers of the <em>incoming</em> server request currently being handled — the
 * zero-dependency analog of the .NET {@code IHttpContextAccessor} that {@code TestTrackingMessageHandler}'s
 * {@code ForwardHeaders} reads. A server adapter (e.g. the servlet filter) opens a {@link Scope} for the
 * duration of request handling, exposing a name→value lookup; the outgoing HTTP client adapters consult it to
 * implement {@code headersToForward} (copy named incoming headers onto downstream calls so a chain of tracked
 * services shares context).
 *
 * <p>{@code ThreadLocal}-backed (like {@link TestIdentityScope}): it covers synchronous request handling on
 * the request thread. Headers do not propagate across a thread hop (a reactive/async downstream call made on a
 * different scheduler) — the same boundary as the rest of the ambient context. Clearing is mandatory; always
 * use the returned {@link Scope} in try-with-resources.
 */
public final class IncomingRequestHeaders {

    private static final ThreadLocal<Function<String, String>> CURRENT = new ThreadLocal<>();

    private IncomingRequestHeaders() {
    }

    /** Opens an ambient scope exposing {@code lookup} (header name → first value, or {@code null}); restores
     *  the previous lookup on {@link Scope#close()} so scopes nest. */
    public static Scope begin(Function<String, String> lookup) {
        Function<String, String> previous = CURRENT.get();
        CURRENT.set(lookup);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    /** The current incoming request's value for {@code name}, or {@code null} when no scope is active / absent. */
    public static String get(String name) {
        Function<String, String> lookup = CURRENT.get();
        if (lookup == null || name == null) {
            return null;
        }
        try {
            return lookup.apply(name);
        } catch (RuntimeException ignored) {
            return null; // a misbehaving lookup must never break the outgoing call
        }
    }

    /** Clears any active lookup (teardown safety net). */
    public static void clear() {
        CURRENT.remove();
    }

    /** The {@link AutoCloseable} handle returned by {@link #begin}; {@code close()} cannot throw. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
