package io.kronikol.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
import io.kronikol.mongodb.AtlasDataApiTracking.AtlasDataApiTrackingOptions;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@link AtlasDataApiTrackingInterceptor} through MockWebServer (no Atlas) to verify it classifies
 * the {@code /action/{name}} request, emits the request/response pair with the clean URI + label, honours
 * verbosity, and applies the excluded-operation / no-test-context gates — the .NET
 * {@code AtlasDataApiTrackingMessageHandler} behaviour.
 */
class AtlasDataApiTrackingInterceptorTest {

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

    private OkHttpClient clientWith(AtlasDataApiTrackingOptions options) {
        return new OkHttpClient.Builder()
            .addInterceptor(new AtlasDataApiTrackingInterceptor(options))
            .build();
    }

    private Response postAction(OkHttpClient client, String action, String body) throws IOException {
        Request req = new Request.Builder()
            .url(server.url("/app/data-abc/endpoint/data/v1/action/" + action))
            .post(RequestBody.create(body, MediaType.get("application/json")))
            .build();
        return client.newCall(req).execute();
    }

    private static AtlasDataApiTrackingOptions baseOptions() {
        return AtlasDataApiTrackingOptions.forService("Atlas")
            .withCallerName("Test")
            .withTestInfoFetcher(() -> new TestInfo("MyTest", "id-1"));
    }

    @Test
    void classifiesAndRecordsAFindOne() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"document\":{\"_id\":1}}"));
        String body = "{\"dataSource\":\"Cluster0\",\"database\":\"shop\",\"collection\":\"orders\","
            + "\"filter\":{\"_id\":1}}";

        try (Response r = postAction(clientWith(baseOptions()), "findOne", body)) {
            assertThat(r.code()).isEqualTo(200);
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("FindOne ← orders"); // classifier Detailed label
        assertThat(req.uri().toString()).isEqualTo("atlas:///shop/orders"); // clean URI
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.ATLAS_DATA_API);
        assertThat(req.content()).contains("Cluster0"); // request body captured at Detailed
        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of(200));
        assertThat(res.content()).contains("document"); // response body captured
        assertThat(req.traceId()).isEqualTo(res.traceId());
    }

    @Test
    void summarisedOmitsBodiesAndUsesTheLabel() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"document\":{}}"));
        String body = "{\"database\":\"shop\",\"collection\":\"orders\"}";

        try (Response r = postAction(
                clientWith(baseOptions().withVerbosity(TrackingVerbosity.SUMMARISED)), "insertOne", body)) {
            assertThat(r.code()).isEqualTo(200);
        }

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("InsertOne"); // Summarised → operation name only
        assertThat(logs.get(0).content()).isNull(); // request body dropped
        assertThat(logs.get(1).content()).isNull(); // response body dropped
    }

    @Test
    void excludedOperationIsNotRecordedButStillForwarded() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        var options = baseOptions().withExcludedOperations(Set.of(AtlasDataApiOperation.DELETE_MANY));

        try (Response r = postAction(clientWith(options), "deleteMany",
                "{\"database\":\"shop\",\"collection\":\"orders\"}")) {
            assertThat(r.code()).isEqualTo(200); // request still forwarded
        }
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty(); // but not tracked
    }

    @Test
    void noTestContextSkipsRecording() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        var options = AtlasDataApiTrackingOptions.forService("Atlas"); // no testInfoFetcher → null identity

        try (Response r = postAction(clientWith(options), "findOne",
                "{\"database\":\"shop\",\"collection\":\"orders\"}")) {
            assertThat(r.code()).isEqualTo(200);
        }
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
