package io.kronikol.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.TrackingHeaders;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.naming.ExcludedHosts;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
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

class KronikolWebClientFilterTest {

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
        // Use the JDK connector so the test needs no reactor-netty on the classpath.
        return WebClient.builder()
            .clientConnector(new JdkClientHttpConnector())
            .filter(new KronikolWebClientFilter(config))
            .build();
    }

    private static HttpTrackingConfig.Builder baseConfig() {
        return HttpTrackingConfig.builder()
            .fixedServiceName("Orders")
            .callerName("Test")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(5));
    }

    private String post(WebClient client, String body) {
        return client.post().uri(server.url("/api").uri())
            .bodyValue(body)
            .retrieve().bodyToMono(String.class).block();
    }

    @Test
    void capturesResponseAndInjectsHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"ok\":true}"));

        String responseBody = post(clientWith(baseConfig().build()), "{\"a\":1}");
        assertThat(responseBody).isEqualTo("{\"ok\":true}"); // caller still reads the re-supplied body

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method()).isEqualTo(Method.Http.POST);
        assertThat(req.serviceName()).isEqualTo("Orders");

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of(201));
        assertThat(res.content()).isEqualTo("{\"ok\":true}");
        assertThat(req.traceId()).isEqualTo(res.traceId());
        assertThat(req.requestResponseId()).isEqualTo(res.requestResponseId());

        RecordedRequest sent = server.takeRequest();
        assertThat(sent.getHeader(TrackingHeaders.CURRENT_TEST_NAME)).isEqualTo("MyTest");
        assertThat(sent.getHeader(TrackingHeaders.CURRENT_TEST_ID)).isEqualTo("id-1");
        assertThat(sent.getHeader(TrackingHeaders.CALLER_NAME)).isEqualTo("Test");
        assertThat(sent.getHeader("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-00");
    }

    @Test
    void excludedHostIsNotTracked() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig config = baseConfig()
            .excludedHosts(ExcludedHosts.of(List.of(server.getHostName())))
            .build();

        assertThat(post(clientWith(config), "x")).isEqualTo("ok");
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
        assertThat(server.takeRequest().getHeader(TrackingHeaders.CURRENT_TEST_NAME)).isNull();
    }

    @Test
    void summarisedVerbosityOmitsResponseBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        HttpTrackingConfig config = baseConfig().verbosity(TrackingVerbosity.SUMMARISED).build();

        assertThat(post(clientWith(config), "{\"a\":1}")).isEqualTo("{\"ok\":true}");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(1).content()).isNull();
    }

    @Test
    void noTestContextMeansNoTracking() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig config = baseConfig().testInfoFetcher(() -> null).build();

        assertThat(post(clientWith(config), "x")).isEqualTo("ok");
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
