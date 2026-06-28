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
import io.kronikol.core.tracking.TrackingVerbosity;
import java.io.IOException;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KronikolOkHttpInterceptorTest {

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

    private OkHttpClient clientWith(HttpTrackingConfig options) {
        return new OkHttpClient.Builder()
            .addInterceptor(new KronikolOkHttpInterceptor(options))
            .build();
    }

    private static HttpTrackingConfig.Builder baseOptions() {
        return HttpTrackingConfig.builder()
            .fixedServiceName("Orders")
            .callerName("Test")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(99));
    }

    private Response post(OkHttpClient client, String body) throws IOException {
        Request req = new Request.Builder()
            .url(server.url("/api"))
            .post(RequestBody.create(body, MediaType.get("application/json")))
            .build();
        return client.newCall(req).execute();
    }

    @Test
    void forwardsConfiguredHeadersFromTheIncomingRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig config = baseOptions()
            .headersToForward(List.of("X-Tenant", "X-Correlation-Id", "X-Absent")).build();

        // Simulate handling an incoming server request carrying some of those headers.
        java.util.Map<String, String> incoming =
            java.util.Map.of("X-Tenant", "acme", "X-Correlation-Id", "corr-7");
        try (var scope = io.kronikol.core.context.IncomingRequestHeaders.begin(incoming::get);
             Response response = post(clientWith(config), "{\"a\":1}")) {
            assertThat(response.code()).isEqualTo(200);
        }

        RecordedRequest sent = server.takeRequest();
        assertThat(sent.getHeader("X-Tenant")).isEqualTo("acme");          // present incoming → forwarded
        assertThat(sent.getHeader("X-Correlation-Id")).isEqualTo("corr-7");
        assertThat(sent.getHeader("X-Absent")).isNull();                   // not incoming → skipped
    }

    @Test
    void forwardsNothingWhenNoIncomingRequestScopeIsActive() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig config = baseOptions().headersToForward(List.of("X-Tenant")).build();

        try (Response response = post(clientWith(config), "{\"a\":1}")) {
            assertThat(response.code()).isEqualTo(200);
        }

        assertThat(server.takeRequest().getHeader("X-Tenant")).isNull(); // no ambient source → no-op
    }

    @Test
    void consumesAmbientDiagramFocusOntoTheLogPair() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        io.kronikol.core.tracking.DiagramFocus.request("a");
        io.kronikol.core.tracking.DiagramFocus.response("ok");
        try {
            try (Response response = post(clientWith(baseOptions().build()), "{\"a\":1}")) {
                assertThat(response.code()).isEqualTo(200);
            }
            List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
            assertThat(logs.get(0).focusFields()).containsExactly("a");   // request note focus
            assertThat(logs.get(1).focusFields()).containsExactly("ok");  // response note focus
            // consumed once — a second tracked call carries no focus
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            RequestResponseLogger.clear();
            try (Response r2 = post(clientWith(baseOptions().build()), "{\"a\":2}")) {
                assertThat(r2.code()).isEqualTo(200);
            }
            assertThat(RequestResponseLogger.getAllLogs().get(0).focusFields()).isNull();
        } finally {
            io.kronikol.core.tracking.DiagramFocus.clearAll();
        }
    }

    @Test
    void capturesRequestResponsePairWithInjectedHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"ok\":true}"));

        try (Response response = post(clientWith(baseOptions().build()), "{\"a\":1}")) {
            assertThat(response.code()).isEqualTo(201);
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.testName()).isEqualTo("MyTest");
        assertThat(req.method()).isEqualTo(Method.Http.POST);
        assertThat(req.serviceName()).isEqualTo("Orders");
        assertThat(req.callerName()).isEqualTo("Test");
        assertThat(req.content()).isEqualTo("{\"a\":1}");

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isEqualTo(io.kronikol.core.tracking.StatusCode.of(201));
        assertThat(res.content()).isEqualTo("{\"ok\":true}");

        // shared correlation
        assertThat(req.traceId()).isEqualTo(res.traceId());
        assertThat(req.requestResponseId()).isEqualTo(res.requestResponseId());

        // headers stamped on the wire for downstream correlation
        RecordedRequest sent = server.takeRequest();
        assertThat(sent.getHeader(TrackingHeaders.CURRENT_TEST_NAME)).isEqualTo("MyTest");
        assertThat(sent.getHeader(TrackingHeaders.CURRENT_TEST_ID)).isEqualTo("id-1");
        assertThat(sent.getHeader(TrackingHeaders.CALLER_NAME)).isEqualTo("Test");
        assertThat(sent.getHeader(TrackingHeaders.TRACE_ID)).isNotBlank();
        assertThat(sent.getHeader("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-00");
    }

    @Test
    void excludedHostIsNotTracked() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig options = baseOptions()
            .excludedHosts(ExcludedHosts.of(List.of(server.getHostName())))
            .build();

        try (Response r = post(clientWith(options), "x")) {
            assertThat(r.code()).isEqualTo(200);
        }

        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
        // and no tracking headers were stamped
        assertThat(server.takeRequest().getHeader(TrackingHeaders.CURRENT_TEST_NAME)).isNull();
    }

    @Test
    void noTestContextMeansNoTracking() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        HttpTrackingConfig options = baseOptions().testInfoFetcher(() -> null).build();

        try (Response r = post(clientWith(options), "x")) {
            assertThat(r.code()).isEqualTo(200);
        }
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void summarisedVerbosityOmitsBodies() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        HttpTrackingConfig options = baseOptions().verbosity(TrackingVerbosity.SUMMARISED).build();

        try (Response r = post(clientWith(options), "{\"a\":1}")) {
            assertThat(r.code()).isEqualTo(200);
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).content()).isNull();
        assertThat(logs.get(1).content()).isNull();
    }

    @Test
    void existingTraceparentIsNotOverwritten() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        OkHttpClient client = clientWith(baseOptions().build());

        Request req = new Request.Builder()
            .url(server.url("/api"))
            .header("traceparent", "00-11111111111111111111111111111111-2222222222222222-01")
            .post(RequestBody.create("x", MediaType.get("text/plain")))
            .build();
        try (Response r = client.newCall(req).execute()) {
            assertThat(r.code()).isEqualTo(200);
        }

        assertThat(server.takeRequest().getHeader("traceparent"))
            .isEqualTo("00-11111111111111111111111111111111-2222222222222222-01");
    }
}
