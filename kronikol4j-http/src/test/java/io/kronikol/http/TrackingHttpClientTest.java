package io.kronikol.http;

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
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackingHttpClientTest {

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

    private HttpClient trackedClient(HttpTrackingConfig config) {
        return new TrackingHttpClient(HttpClient.newHttpClient(), config);
    }

    private static HttpTrackingConfig.Builder baseConfig() {
        return HttpTrackingConfig.builder()
            .fixedServiceName("Orders")
            .callerName("Test")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(7));
    }

    private HttpRequest postRequest(String body) {
        return HttpRequest.newBuilder(server.url("/api").uri())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    }

    @Test
    void capturesPairWithTeeRequestBodyAndInjectedHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"ok\":true}"));

        HttpResponse<String> response = trackedClient(baseConfig().build())
            .send(postRequest("{\"a\":1}"), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method()).isEqualTo(Method.Http.POST);
        assertThat(req.serviceName()).isEqualTo("Orders");
        assertThat(req.content()).isEqualTo("{\"a\":1}"); // captured via the tee publisher

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of(201));
        assertThat(res.content()).isEqualTo("{\"ok\":true}");
        assertThat(req.traceId()).isEqualTo(res.traceId());
        assertThat(req.requestResponseId()).isEqualTo(res.requestResponseId());

        RecordedRequest sent = server.takeRequest();
        assertThat(sent.getBody().readUtf8()).isEqualTo("{\"a\":1}"); // body actually sent intact
        assertThat(sent.getHeader(TrackingHeaders.CURRENT_TEST_NAME)).isEqualTo("MyTest");
        assertThat(sent.getHeader(TrackingHeaders.CURRENT_TEST_ID)).isEqualTo("id-1");
        assertThat(sent.getHeader(TrackingHeaders.CALLER_NAME)).isEqualTo("Test");
        assertThat(sent.getHeader("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-00");
    }

    @Test
    void sendAsyncAlsoCaptures() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("pong"));

        HttpResponse<String> response = trackedClient(baseConfig().build())
            .sendAsync(postRequest("ping"), HttpResponse.BodyHandlers.ofString())
            .get();

        assertThat(response.statusCode()).isEqualTo(200);
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).content()).isEqualTo("ping");
        assertThat(logs.get(1).content()).isEqualTo("pong");
    }

    @Test
    void excludedHostIsNotTracked() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig config = baseConfig()
            .excludedHosts(ExcludedHosts.of(List.of(server.getHostName())))
            .build();

        HttpResponse<String> r = trackedClient(config)
            .send(postRequest("x"), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);

        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
        assertThat(server.takeRequest().getHeader(TrackingHeaders.CURRENT_TEST_NAME)).isNull();
    }

    @Test
    void summarisedVerbosityOmitsBodies() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        HttpTrackingConfig config = baseConfig().verbosity(TrackingVerbosity.SUMMARISED).build();

        trackedClient(config).send(postRequest("{\"a\":1}"), HttpResponse.BodyHandlers.ofString());

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).content()).isNull();
        assertThat(logs.get(1).content()).isNull();
        // body still sent on the wire even though not captured
        assertThat(server.takeRequest().getBody().readUtf8()).isEqualTo("{\"a\":1}");
    }

    @Test
    void noTestContextMeansNoTracking() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig config = baseConfig().testInfoFetcher(() -> null).build();

        trackedClient(config).send(postRequest("x"), HttpResponse.BodyHandlers.ofString());
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
