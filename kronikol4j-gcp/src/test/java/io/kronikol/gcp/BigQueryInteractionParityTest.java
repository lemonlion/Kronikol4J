package io.kronikol.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.gcp.GcpTracking.GcpTrackingOptions;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end cross-runtime capture parity for GCP BigQuery: drives the <em>real
 * {@link GcpHttpTrackingInterceptor}</em> (installed via its documented {@code initializer(...)} on a
 * google-http-client request factory — the same hook the BigQuery client uses) against a live BigQuery
 * emulator (Testcontainers {@code ghcr.io/goccy/bigquery-emulator}) and diffs the emitted
 * {@link RequestResponseLog}s against the captured output of the <em>real .NET
 * {@code BigQueryTrackingMessageHandler}</em> driven against the same emulator (fixture
 * {@code bigquery-interactions.txt}, harness {@code KRON_BQ_E2E=1}). Both runtimes issue the identical REST
 * paths ({@code POST …/datasets}, {@code GET …/datasets/ds1}); the interceptor routes by the {@code /bigquery/}
 * path (not host), so no emulator host trick is needed.
 *
 * <p><strong>Result:</strong> byte-identical on type, the {@code Create/Read} label, the host-normalised clean
 * URI, and the response-half status (200). The pinned divergence is the body content: the Java google-http-client
 * {@code HttpResponseInterceptor} only sees the request method/URL + response status (it passes no body to the
 * recorder), whereas the .NET {@code DelegatingHandler} reads request/response content. The same interceptor-hook
 * limitation flagged for Elasticsearch / S3 / SQS / DynamoDB.
 */
class BigQueryInteractionParityTest {

    private static GenericContainer<?> emulator;
    private static String base;
    private static HttpRequestFactory factory;

    @BeforeAll
    static void startEmulator() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
            "No Docker available — skipping the live-BigQuery-emulator parity test");
        emulator = new GenericContainer<>(DockerImageName.parse("ghcr.io/goccy/bigquery-emulator:latest"))
            .withExposedPorts(9050)
            .withCommand("--project=test-project", "--dataset=ds0")
            .waitingFor(Wait.forHttp("/bigquery/v2/projects/test-project/datasets").forPort(9050)
                .forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));
        emulator.start();
        base = "http://" + emulator.getHost() + ":" + emulator.getMappedPort(9050);
        factory = new NetHttpTransport().createRequestFactory(GcpHttpTrackingInterceptor.initializer(
            new GcpTrackingOptions("Gcp", io.kronikol.core.tracking.TrackingDefaults.CALLER_NAME,
                () -> new TestInfo("MyTest", "t1"))));
    }

    @AfterAll
    static void stop() {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (emulator != null) {
            emulator.stop();
        }
    }

    @Test
    void javaBigQueryCaptureMatchesDotNetOnTheClassificationFields() throws IOException {
        try { // cleanup any prior ds1 (untracked-ish: cleared below)
            factory.buildDeleteRequest(new GenericUrl(
                base + "/bigquery/v2/projects/test-project/datasets/ds1")).execute().disconnect();
        } catch (HttpResponseException ignored) {
            // 404 if absent — fine.
        }

        RequestResponseLogger.clear();
        String body = "{\"datasetReference\":{\"datasetId\":\"ds1\",\"projectId\":\"test-project\"}}";
        factory.buildPostRequest(new GenericUrl(base + "/bigquery/v2/projects/test-project/datasets"),
            new ByteArrayContent("application/json", body.getBytes(StandardCharsets.UTF_8))).execute().disconnect();
        factory.buildGetRequest(new GenericUrl(
            base + "/bigquery/v2/projects/test-project/datasets/ds1")).execute().disconnect();

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/bigquery-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + g[0] + " " + g[1] + ")";
            // (1) type | label | host-normalised clean URI | status byte-identical on every line.
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " label").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);
            assertThat(a[4]).as(where + " status").isEqualTo(g[4]);
        }

        // (2) The divergence: body content. The Java google-http-client interceptor passes no body to the
        //     recorder (it only has method/URL + status), so every content cell is ~null~; the .NET handler
        //     reads request/response bodies. Inherent interceptor-hook limitation (flagged like ES/S3/SQS/DDB).
        for (String[] a : actual) {
            assertThat(a[3]).as("java content (interceptor captures none)").isEqualTo("~null~");
        }
        // .NET captured a body wherever the HTTP exchange carried one (POST request, both responses).
        assertThat(content(golden, "Create", "Request")).isEqualTo("<body>");
        assertThat(content(golden, "Create", "Response")).isEqualTo("<body>");
        assertThat(content(golden, "Read", "Response")).isEqualTo("<body>");
    }

    private static String content(List<String[]> rows, String label, String type) {
        return rows.stream().filter(r -> r[1].equals(label) && r[0].equals(type)).findFirst().orElseThrow()[3];
    }

    private static List<String[]> project(List<RequestResponseLog> logs) {
        List<String[]> out = new ArrayList<>();
        for (RequestResponseLog l : logs) {
            String uri = l.uri() == null ? "~null~"
                : l.uri().toString().replaceFirst("^([a-z]+://)[^/]+", "$1HOST");
            out.add(new String[] {
                l.type().toString().equals("REQUEST") ? "Request" : "Response",
                l.method() == null ? "~null~" : l.method().value(),
                uri,
                (l.content() == null || l.content().isEmpty()) ? "~null~" : "<body>",
                l.statusCode() == null ? "~null~" : statusText(l.statusCode())});
        }
        return out;
    }

    private static String statusText(StatusCode s) {
        return s instanceof StatusCode.Http h ? String.valueOf(h.code()) : ((StatusCode.Custom) s).value();
    }

    private static List<String[]> parse(String golden) {
        List<String[]> out = new ArrayList<>();
        for (String line : golden.split("\n")) {
            if (!line.isEmpty()) {
                out.add(line.split("\\|", -1));
            }
        }
        return out;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = BigQueryInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
