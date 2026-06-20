package io.kronikol.core.context;

import java.util.Collection;
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
            String key = batch.stream().map(keySelector).filter(k -> k != null).findFirst().orElse(null);
            try (var ignored = key == null ? null : CorrelatedProcessingScope.begin(key)) {
                handler.accept(batch);
            }
        };
    }
}
