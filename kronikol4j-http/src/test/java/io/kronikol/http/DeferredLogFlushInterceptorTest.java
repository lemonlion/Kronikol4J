package io.kronikol.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.PendingLogEntry;
import io.kronikol.core.tracking.PendingRequestResponseLogs;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link DeferredLogFlushInterceptor} drains {@link PendingRequestResponseLogs} after an HTTP
 * exchange, attributing the deferred entries to the resolved test — and leaves them queued when there is no
 * test context (the .NET {@code DeferredLogFlushHandler} behaviour).
 */
class DeferredLogFlushInterceptorTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RequestResponseLogger.clear();
        PendingRequestResponseLogs.clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        RequestResponseLogger.clear();
        PendingRequestResponseLogs.clear();
    }

    private static void enqueueOne() {
        PendingRequestResponseLogs.enqueue(PendingLogEntry.builder()
            .serviceName("Cache").callerName("Test")
            .method(Method.of("GET")).requestContent("key").responseContent("value")
            .uri(URI.create("redis://cache/")).statusCode(StatusCode.of("OK"))
            .dependencyCategory(io.kronikol.core.constants.DependencyCategories.REDIS)
            .build());
    }

    private Response get(OkHttpClient client) throws IOException {
        return client.newCall(new Request.Builder().url(server.url("/api")).build()).execute();
    }

    @Test
    void flushesPendingLogsAfterTheResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        enqueueOne();
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new DeferredLogFlushInterceptor(
                (Supplier<TestInfo>) () -> new TestInfo("MyTest", "id-1"), IdGenerator.seeded(1)))
            .build();

        try (Response r = get(client)) {
            assertThat(r.code()).isEqualTo(200);
        }

        assertThat(PendingRequestResponseLogs.count()).isZero(); // queue drained
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2); // the deferred entry emitted as a request+response pair
        assertThat(logs.get(0).type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(logs.get(0).testName()).isEqualTo("MyTest"); // attributed to the resolved test
        assertThat(logs.get(0).serviceName()).isEqualTo("Cache");
        assertThat(logs.get(1).type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(logs.get(0).traceId()).isEqualTo(logs.get(1).traceId());
    }

    @Test
    void leavesEntriesQueuedWhenNoTestContext() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        enqueueOne();
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new DeferredLogFlushInterceptor(
                (Supplier<TestInfo>) () -> null, IdGenerator.seeded(1))) // no identity resolvable
            .build();

        try (Response r = get(client)) {
            assertThat(r.code()).isEqualTo(200);
        }

        assertThat(PendingRequestResponseLogs.count()).isEqualTo(1); // not flushed — remains for next time
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void nothingPendingIsANoOp() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new DeferredLogFlushInterceptor(
                (Supplier<TestInfo>) () -> new TestInfo("MyTest", "id-1"), IdGenerator.seeded(1)))
            .build();

        try (Response r = get(client)) {
            assertThat(r.code()).isEqualTo(204);
        }
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
