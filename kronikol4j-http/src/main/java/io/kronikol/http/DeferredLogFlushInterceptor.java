package io.kronikol.http;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.PendingRequestResponseLogs;
import java.io.IOException;
import java.util.function.Supplier;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * An OkHttp {@link Interceptor} that flushes {@link PendingRequestResponseLogs} after each HTTP response —
 * the Java analog of the .NET {@code DeferredLogFlushHandler} (a client {@code DelegatingHandler}). Some
 * trackers capture an interaction before the owning test identity is known and {@code enqueue} it onto the
 * pending queue; this interceptor drains that queue once an HTTP exchange completes (the point where the
 * ambient identity is reliably resolvable), attributing the deferred entries to the current test.
 *
 * <p>Install it as an application interceptor <em>outside</em> {@link KronikolOkHttpInterceptor} in the chain
 * (i.e. added first, so it wraps the tracking interceptor and runs after the full request/response cycle),
 * mirroring .NET's "place OUTSIDE TestTrackingMessageHandler" guidance:
 *
 * <pre>{@code new OkHttpClient.Builder()
 *         .addInterceptor(new DeferredLogFlushInterceptor(config))   // outer — flushes after the response
 *         .addInterceptor(new KronikolOkHttpInterceptor(config))     // inner — records the exchange
 *         .build();}</pre>
 *
 * <p>If the identity cannot be resolved (no test context — e.g. during startup), the flush is skipped and the
 * entries remain queued for the next successful exchange, exactly as the .NET handler swallows a throwing
 * fetcher. The OkHttp dependency is {@code compileOnly}.
 */
public final class DeferredLogFlushInterceptor implements Interceptor {

    private final Supplier<TestInfo> testInfoFetcher;
    private final IdGenerator ids;

    /** Flushes attributed to the test resolved from {@code testInfoFetcher}, with ids from {@code ids}. */
    public DeferredLogFlushInterceptor(Supplier<TestInfo> testInfoFetcher, IdGenerator ids) {
        this.testInfoFetcher = testInfoFetcher;
        this.ids = ids == null ? IdGenerator.random() : ids;
    }

    /** Convenience overload reusing an {@link HttpTrackingConfig}'s identity fetcher + id generator. */
    public DeferredLogFlushInterceptor(HttpTrackingConfig options) {
        this(options.testInfoFetcher(), options.ids());
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        if (PendingRequestResponseLogs.count() > 0) {
            TestInfo who = TestInfoResolver.resolve(testInfoFetcher);
            if (who != null) {
                // No test context → leave the entries queued for the next successful exchange (.NET parity).
                PendingRequestResponseLogs.flushAll(who.name(), who.id(), ids);
            }
        }

        return response;
    }
}
