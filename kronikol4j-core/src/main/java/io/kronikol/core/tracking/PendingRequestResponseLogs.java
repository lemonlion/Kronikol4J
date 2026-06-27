package io.kronikol.core.tracking;

import io.kronikol.core.support.IdGenerator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe queue for deferring request/response log entries until test identity is available. Java port
 * of the .NET {@code PendingRequestResponseLogs}.
 *
 * <p>A consumer that captures an interaction before it knows which test owns it {@link #enqueue}s a
 * {@link PendingLogEntry}; once identity resolves (e.g. after an HTTP request completes, or when a proxy in
 * deferred mode learns the test), it calls {@link #flushAll} to emit each pending entry as a request+response
 * pair sharing one trace id and request-response id, via {@link RequestResponseLogger}.
 *
 * <p>The HTTP {@code DeferredLogFlushHandler} (a client {@code DelegatingHandler} that flushes after each
 * response) is HTTP-specific and lives with the HTTP adapter; this queue + flush is the shared mechanism.
 */
public final class PendingRequestResponseLogs {

    private static final ConcurrentLinkedQueue<PendingLogEntry> PENDING = new ConcurrentLinkedQueue<>();

    private PendingRequestResponseLogs() {
    }

    /** Queues an entry for later flushing. */
    public static void enqueue(PendingLogEntry entry) {
        PENDING.add(entry);
    }

    /** The number of entries currently queued. */
    public static int count() {
        return PENDING.size();
    }

    /**
     * Drains the queue, emitting each entry as a request+response pair attributed to the given test.
     * Fresh trace + request-response ids come from {@code ids} (the determinism seam — pass a seeded
     * generator in tests/golden capture).
     */
    public static void flushAll(String testName, String testId, IdGenerator ids) {
        PendingLogEntry entry;
        while ((entry = PENDING.poll()) != null) {
            UUID traceId = ids.newId();
            UUID requestResponseId = ids.newId();

            RequestResponseLogger.log(RequestResponseLog.builder()
                .testName(testName).testId(testId)
                .method(entry.method()).content(entry.requestContent()).uri(entry.uri())
                .headers(List.of()).serviceName(entry.serviceName()).callerName(entry.callerName())
                .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
                .trackingIgnore(false).dependencyCategory(entry.dependencyCategory())
                .timestamp(entry.timestamp()).build()
                .activityTraceId(entry.activityTraceId()).activitySpanId(entry.activitySpanId()));

            RequestResponseLogger.log(RequestResponseLog.builder()
                .testName(testName).testId(testId)
                .method(entry.method()).content(entry.responseContent()).uri(entry.uri())
                .headers(List.of()).serviceName(entry.serviceName()).callerName(entry.callerName())
                .type(RequestResponseType.RESPONSE).traceId(traceId).requestResponseId(requestResponseId)
                .trackingIgnore(false).statusCode(entry.statusCode()).dependencyCategory(entry.dependencyCategory())
                .timestamp(entry.timestamp()).build()
                .activityTraceId(entry.activityTraceId()).activitySpanId(entry.activitySpanId()));
        }
    }

    /** Discards all queued entries without emitting them. */
    public static void clear() {
        PENDING.clear();
    }
}
