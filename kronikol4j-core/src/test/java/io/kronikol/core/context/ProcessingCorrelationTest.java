package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the async / Callable / Runnable correlation wrappers establish the correct
 * {@link TestIdentityScope} for the duration of the handler's execution and clear it afterwards.
 *
 * <p>Threading note: a Java {@link ThreadLocal} scope (unlike .NET's {@code AsyncLocal}) cannot flow into
 * {@code CompletableFuture} continuations that hop threads. The wrappers therefore establish the scope on
 * the thread that runs the handler (for {@code Callable}/{@code Runnable}, the executing thread; for the
 * async form, the synchronous launch). Cross-thread continuation attribution uses the data-keyed
 * {@link TestCorrelationStore}, which is exactly what these wrappers resolve against.
 */
class ProcessingCorrelationTest {

    @AfterEach
    void cleanup() {
        TestCorrelationStore.clear();
        TestIdentityScope.clear();
    }

    @Test
    void wrapCallableRunsUnderResolvedScopeThenClears() throws Exception {
        TestCorrelationStore.correlate("job-1", "MyTest", "id-1");
        AtomicReference<TestInfo> seen = new AtomicReference<>();

        var wrapped = ProcessingCorrelation.wrapCallable("job-1", () -> {
            seen.set(TestIdentityScope.current());
            return 42;
        });

        Integer result = wrapped.call();

        assertThat(result).isEqualTo(42);
        assertThat(seen.get()).isEqualTo(new TestInfo("MyTest", "id-1"));
        assertThat(TestIdentityScope.current()).isNull(); // cleared after
    }

    @Test
    void wrapRunnableRunsUnderResolvedScopeThenClears() {
        TestCorrelationStore.correlate("job-2", "MyTest", "id-2");
        AtomicReference<TestInfo> seen = new AtomicReference<>();

        var wrapped = ProcessingCorrelation.wrapRunnable("job-2", () -> seen.set(TestIdentityScope.current()));
        wrapped.run();

        assertThat(seen.get()).isEqualTo(new TestInfo("MyTest", "id-2"));
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void unknownKeyRunsUnattributed() throws Exception {
        AtomicReference<TestInfo> seen = new AtomicReference<>();
        var wrapped = ProcessingCorrelation.wrapCallable("missing", () -> {
            seen.set(TestIdentityScope.current());
            return "ok";
        });
        assertThat(wrapped.call()).isEqualTo("ok");
        assertThat(seen.get()).isNull(); // no scope, but still runs
    }

    @Test
    void wrapAsyncEstablishesScopeForHandlerLaunchAndClears() {
        TestCorrelationStore.correlate("evt-1", "AsyncTest", "id-3");
        AtomicReference<TestInfo> seen = new AtomicReference<>();

        Function<String, CompletionStage<Void>> handler = item -> {
            seen.set(TestIdentityScope.current()); // observed during synchronous launch
            return CompletableFuture.completedFuture(null);
        };
        var wrapped = ProcessingCorrelation.wrapAsync(handler, item -> "evt-1");

        CompletionStage<Void> stage = wrapped.apply("payload");

        assertThat(stage.toCompletableFuture().join()).isNull();
        assertThat(seen.get()).isEqualTo(new TestInfo("AsyncTest", "id-3"));
        assertThat(TestIdentityScope.current()).isNull(); // cleared on the launching thread
    }

    @Test
    void wrapSyncRunsUnderResolvedScopeThenClears() {
        // wrapSync is the .NET WrapSync naming alias of wrap(Consumer, keySelector).
        TestCorrelationStore.correlate("sync-1", "SyncTest", "id-s");
        AtomicReference<TestInfo> seen = new AtomicReference<>();

        Consumer<String> wrapped = ProcessingCorrelation.wrapSync(
            item -> seen.set(TestIdentityScope.current()), item -> "sync-1");
        wrapped.accept("payload");

        assertThat(seen.get()).isEqualTo(new TestInfo("SyncTest", "id-s"));
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void wrapAsyncForwardsCancellationSignalToHandler() {
        TestCorrelationStore.correlate("evt-c", "CancelTest", "id-c");
        AtomicReference<TestInfo> seen = new AtomicReference<>();
        AtomicBoolean handlerSawCancellation = new AtomicBoolean(false);

        BiFunction<String, BooleanSupplier, CompletionStage<Void>> handler = (item, cancelled) -> {
            seen.set(TestIdentityScope.current());
            handlerSawCancellation.set(cancelled.getAsBoolean()); // the token is threaded through
            return CompletableFuture.completedFuture(null);
        };
        var wrapped = ProcessingCorrelation.wrapAsync(handler, item -> "evt-c");

        wrapped.apply("payload", () -> true).toCompletableFuture().join();

        assertThat(seen.get()).isEqualTo(new TestInfo("CancelTest", "id-c"));
        assertThat(handlerSawCancellation).isTrue();
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void wrapBatchAsyncWithCancellationForwardsSignalAndScopesFromFirstCorrelatable() {
        TestCorrelationStore.correlate("bk2", "BatchCancelTest", "id-bc");
        AtomicReference<TestInfo> seen = new AtomicReference<>();
        AtomicBoolean handlerSawCancellation = new AtomicBoolean(true);

        BiFunction<Collection<String>, BooleanSupplier, CompletionStage<Void>> handler = (batch, cancelled) -> {
            seen.set(TestIdentityScope.current());
            handlerSawCancellation.set(cancelled.getAsBoolean());
            return CompletableFuture.completedFuture(null);
        };
        var wrapped = ProcessingCorrelation.wrapBatchAsync(handler, item -> item);

        wrapped.apply(List.of("unknown", "bk2"), () -> false).toCompletableFuture().join();

        assertThat(seen.get()).isEqualTo(new TestInfo("BatchCancelTest", "id-bc"));
        assertThat(handlerSawCancellation).isFalse(); // signal forwarded faithfully
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void wrapBatchAsyncUsesFirstCorrelatableItem() {
        TestCorrelationStore.correlate("k2", "BatchTest", "id-4");
        AtomicReference<TestInfo> seen = new AtomicReference<>();

        Function<Collection<String>, CompletionStage<Void>> handler = batch -> {
            seen.set(TestIdentityScope.current());
            return CompletableFuture.completedFuture(null);
        };
        // First item's key is unknown, second resolves — scope must come from the second.
        var wrapped = ProcessingCorrelation.wrapBatchAsync(handler, item -> item);

        wrapped.apply(List.of("unknown", "k2")).toCompletableFuture().join();

        assertThat(seen.get()).isEqualTo(new TestInfo("BatchTest", "id-4"));
        assertThat(TestIdentityScope.current()).isNull();
    }
}
