package io.kronikol.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.http.HttpTrackingConfig;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Verifies {@link KronikolWebClientConnector} — the {@code ClientHttpConnector}-layer tracker that captures
 * <strong>both</strong> the request and response bodies (the filter can only read the response, since a
 * {@code WebClient} request body is a write-only reactive {@code BodyInserter}). Uses the JDK connector +
 * MockWebServer so no reactor-netty is needed.
 */
class KronikolWebClientConnectorTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RequestResponseLogger.clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        RequestResponseLogger.clear();
    }

    private WebClient clientWith(HttpTrackingConfig config) {
        // Force HTTP/1.1 so MockWebServer records a clean request (no h2c upgrade pseudo-headers).
        var jdk = java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build();
        return WebClient.builder()
            .clientConnector(new KronikolWebClientConnector(new JdkClientHttpConnector(jdk), config))
            .build();
    }

    private static HttpTrackingConfig.Builder baseConfig() {
        return HttpTrackingConfig.builder()
            .fixedServiceName("Orders")
            .callerName("Test")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(5));
    }

    @Test
    void capturesBothRequestAndResponseBodies() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        WebClient client = clientWith(baseConfig().build());

        String response = client.post().uri(server.url("/checkout").uri())
            .bodyValue("{\"item\":\"egg\"}")
            .retrieve().bodyToMono(String.class).block();

        assertThat(response).isEqualTo("{\"ok\":true}"); // caller still gets the body (tee, not consume)
        RecordedRequest sent = server.takeRequest();
        assertThat(sent.getBody().readUtf8()).isEqualTo("{\"item\":\"egg\"}"); // body really sent

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        RequestResponseLog request = logs.stream()
            .filter(l -> l.type() == RequestResponseType.REQUEST).findFirst().orElseThrow();
        RequestResponseLog responseLog = logs.stream()
            .filter(l -> l.type() == RequestResponseType.RESPONSE).findFirst().orElseThrow();

        assertThat(request.content()).isEqualTo("{\"item\":\"egg\"}");   // <-- the request-body capture (the gap)
        assertThat(request.serviceName()).isEqualTo("Orders");
        assertThat(responseLog.content()).isEqualTo("{\"ok\":true}");
        assertThat(responseLog.statusCode()).isEqualTo(io.kronikol.core.tracking.StatusCode.of(200));
    }

    @Test
    void injectsIdentityAndTraceHeaders() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        WebClient client = clientWith(baseConfig().build());

        client.post().uri(server.url("/x").uri()).bodyValue("body")
            .retrieve().bodyToMono(String.class).block();

        RecordedRequest sent = server.takeRequest();
        assertThat(sent.getHeader("test-tracking-current-test-id")).isEqualTo("id-1");
        assertThat(sent.getHeader("test-tracking-current-test-name")).isEqualTo("MyTest");
        assertThat(sent.getHeader("traceparent")).isNotNull();
    }

    @Test
    void skipsWhenNoTestContext() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        WebClient client = clientWith(baseConfig().testInfoFetcher(() -> null).build());

        String response = client.post().uri(server.url("/x").uri()).bodyValue("body")
            .retrieve().bodyToMono(String.class).block();

        assertThat(response).isEqualTo("ok");        // pass-through still works
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty(); // nothing tracked
    }
}
