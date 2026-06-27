package io.kronikol.core.context;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Decorators that wrap background handlers so each work-item runs under the correct test's
 * {@link TestIdentityScope}, resolved from {@link TestCorrelationStore}. Parallel-safe.
 * Mirrors the .NET {@code ProcessingCorrelation}.
 */
public final class ProcessingCorrelation {

    private ProcessingCorrelation() {
    }

    /** Wraps a per-item handler; {@code keySelector} derives the correlation key from the item. */
    public static <T> Consumer<T> wrap(Consumer<T> handler, Function<T, String> keySelector) {
        return item -> {
            String key = keySelector.apply(item);
            // try-with-resources tolerates a null scope (unknown key -> unattributed, by design).
            try (var ignored = CorrelatedProcessingScope.begin(key)) {
                handler.accept(item);
            }
        };
    }

    /** Wraps a batch handler, establishing the scope from the first correlatable item. */
    public static <T> Consumer<Collection<T>> wrapBatch(Consumer<Collection<T>> handler,
                                                        Function<T, String> keySelector) {
        return batch -> {
            String key = firstCorrelatableKey(batch, keySelector);
            try (var ignored = key == null ? null : CorrelatedProcessingScope.begin(key)) {
                handler.accept(batch);
            }
        };
    }

    /**
     * Wraps a {@link Callable} so it runs under the scope resolved from {@code key} on whatever thread
     * executes it. This is the correct form for executor submissions ({@code executor.submit(wrapCallable(
     * key, work))}): the key is captured up front, and the {@link ThreadLocal} scope is established on the
     * executing thread for the whole (synchronous) duration of {@code work} — then cleared.
     */
    public static <R> Callable<R> wrapCallable(String key, Callable<R> work) {
        return () -> {
            try (var ignored = CorrelatedProcessingScope.begin(key)) {
                return work.call();
            }
        };
    }

    /** {@link Runnable} sibling of {@link #wrapCallable} for fire-and-forget background work. */
    public static Runnable wrapRunnable(String key, Runnable work) {
        return () -> {
            try (var ignored = CorrelatedProcessingScope.begin(key)) {
                work.run();
            }
        };
    }

    /**
     * Wraps an async per-item handler so the item is attributed to the correct test. The scope is opened
     * for the synchronous launch of {@code handler} (when it builds and returns its {@link CompletionStage})
     * and closed on the same thread.
     *
     * <p><strong>Threading.</strong> Unlike .NET's {@code AsyncLocal}, a Java {@link ThreadLocal} scope does
     * not flow into continuations that hop threads, so logs emitted after the handler suspends onto another
     * thread are not attributed via the scope — they rely on the data-keyed {@link TestCorrelationStore}
     * (which is exactly what this resolves against). Handlers that complete synchronously are fully covered.
     */
    public static <T> Function<T, CompletionStage<Void>> wrapAsync(
        Function<T, CompletionStage<Void>> handler, Function<T, String> keySelector) {
        return item -> {
            String key = keySelector.apply(item);
            try (var ignored = CorrelatedProcessingScope.begin(key)) {
                return handler.apply(item);
            }
        };
    }

    /** Async batch sibling of {@link #wrapAsync}; scope comes from the first correlatable item. */
    public static <T> Function<Collection<T>, CompletionStage<Void>> wrapBatchAsync(
        Function<Collection<T>, CompletionStage<Void>> handler, Function<T, String> keySelector) {
        return batch -> {
            String key = firstCorrelatableKey(batch, keySelector);
            try (var ignored = key == null ? null : CorrelatedProcessingScope.begin(key)) {
                return handler.apply(batch);
            }
        };
    }

    /**
     * The first key in the batch that actually resolves to a test, matching .NET {@code WrapBatch} (which
     * calls {@code Begin} per item and breaks on the first non-null scope). A non-null-but-unresolvable key
     * is skipped — the previous "first non-null key" logic stopped early and left the batch unattributed.
     */
    private static <T> String firstCorrelatableKey(Collection<T> batch, Function<T, String> keySelector) {
        for (T item : batch) {
            String key = keySelector.apply(item);
            if (key != null && TestCorrelationStore.resolve(key) != null) {
                return key;
            }
        }
        return null;
    }
}
