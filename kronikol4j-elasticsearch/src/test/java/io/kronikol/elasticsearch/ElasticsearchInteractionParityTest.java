package io.kronikol.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.elasticsearch.ElasticsearchTracking.ElasticsearchTrackingOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end cross-runtime capture parity for Elasticsearch: drives the <em>real
 * {@link KronikolElasticsearchInterceptor}</em> on the low-level ES {@code RestClient} against a live ES
 * (Testcontainers) and diffs the emitted {@link RequestResponseLog}s against the captured output of the
 * <em>real .NET {@code ElasticsearchTrackingCallbackHandler}</em> driven against a live ES (fixture
 * {@code elasticsearch-interactions.txt}, harness {@code KRON_ES_E2E=1}). ES is matched on major version
 * (9.x) on both sides so the .NET v9 client's compatibility headers are accepted.
 *
 * <p><strong>Result:</strong> the classification is byte-identical cross-runtime — {@code type}, the
 * {@code Index →/Get ←/Search →/Delete} method label, the host-less {@code elasticsearch:///<index>} URI, and
 * the <em>response</em>-half HTTP status all match exactly on every line. Two divergences are <em>pinned</em>,
 * not hidden, because they are genuine limitations of the Java low-level interceptor hook (a deferred,
 * output-changing enhancement — see REMAINING_PARITY.md):
 * <ul>
 *   <li><b>Request-half status</b> — .NET stamps the HTTP status on <em>both</em> halves; the Java
 *       {@code Interactions.recordPair} convention leaves the request half null.</li>
 *   <li><b>Body content</b> — .NET (with {@code DisableDirectStreaming}) captures the request/response bodies;
 *       the Apache HttpCore {@code HttpResponseInterceptor} has no buffered entity, so Java captures none.
 *       The golden records body <em>presence</em> ({@code <body>} vs {@code ~null~}) rather than raw bytes
 *       (Java can never match them, and .NET's search body carries a volatile {@code took}).</li>
 * </ul>
 */
class ElasticsearchInteractionParityTest {

    private static GenericContainer<?> container;
    private static RestClient client;

    @BeforeAll
    static void startEs() {
        String endpoint = System.getProperty("kron.es.endpoint");
        if (endpoint == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "No Docker available and no -Dkron.es.endpoint set — skipping the live-Elasticsearch parity test");
            container = new GenericContainer<>(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.4"))
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .waitingFor(Wait.forHttp("/").forPort(9200).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));
            container.start();
            endpoint = container.getHost() + ":" + container.getMappedPort(9200);
        }
        client = RestClient.builder(HttpHost.create("http://" + endpoint))
            .setHttpClientConfigCallback(b -> b.addInterceptorLast(new KronikolElasticsearchInterceptor(
                ElasticsearchTrackingOptions.forCluster("SearchCluster")
                    .withTestInfoFetcher(() -> new TestInfo("MyTest", "t1")))))
            .build();
    }

    @AfterAll
    static void stop() throws IOException {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (client != null) {
            client.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void javaEsCaptureMatchesDotNetOnTheClassificationFields() throws IOException {
        try {
            client.performRequest(new Request("DELETE", "/orders")); // clean start (tracked, then cleared)
        } catch (ResponseException ignored) {
            // 404 if the index does not exist — fine.
        }

        RequestResponseLogger.clear();
        Request put = new Request("PUT", "/orders/_doc/1");
        put.setJsonEntity("{\"id\":1,\"name\":\"a\"}");
        client.performRequest(put);                                  // Index → orders (201)
        client.performRequest(new Request("GET", "/orders/_doc/1")); // Get ← orders (200)
        Request search = new Request("POST", "/orders/_search");
        search.setJsonEntity("{\"query\":{\"match_all\":{}}}");
        client.performRequest(search);                               // Search → orders (200)
        client.performRequest(new Request("DELETE", "/orders/_doc/1")); // Delete orders (200)

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/elasticsearch-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + g[0] + " " + g[1] + ")";
            // (1) type | method-label | uri are byte-identical on EVERY line (the core classification parity).
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " method/label").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);

            if (g[0].equals("Response")) {
                // (2) Response-half HTTP status is byte-identical (201 for Index, 200 for the rest).
                assertThat(a[4]).as(where + " response status").isEqualTo(g[4]);
                // Java captures no response body; .NET does (LogResponseContent) — pinned divergence.
                assertThat(a[3]).as(where + " java response content").isEqualTo("~null~");
                assertThat(g[3]).as(where + " .NET response content present").isEqualTo("<body>");
            } else {
                // (3) Request-half divergences, pinned: Java leaves request status null (recordPair convention)
                //     where .NET stamps the HTTP status; Java captures no request body where .NET captures it
                //     for body-bearing verbs (PUT/POST) — both genuine interceptor-hook limitations.
                assertThat(a[4]).as(where + " java request status (null by convention)").isEqualTo("~null~");
                assertThat(g[4]).as(where + " .NET request status (stamped)").isNotEqualTo("~null~");
                assertThat(a[3]).as(where + " java request content").isEqualTo("~null~");
            }
        }

        // The request-content presence on the .NET side follows the verb: PUT/POST carry a body, GET/DELETE
        // do not — pinned so a regression in either runtime's body handling is caught.
        assertThat(content(golden, "Index → orders", "Request")).isEqualTo("<body>");
        assertThat(content(golden, "Search → orders", "Request")).isEqualTo("<body>");
        assertThat(content(golden, "Get ← orders", "Request")).isEqualTo("~null~");
        assertThat(content(golden, "Delete orders", "Request")).isEqualTo("~null~");
    }

    private static String content(List<String[]> rows, String label, String type) {
        return rows.stream().filter(r -> r[1].equals(label) && r[0].equals(type)).findFirst().orElseThrow()[3];
    }

    private static List<String[]> project(List<RequestResponseLog> logs) {
        List<String[]> out = new ArrayList<>();
        for (RequestResponseLog l : logs) {
            out.add(new String[] {
                l.type().toString().equals("REQUEST") ? "Request" : "Response",
                l.method() == null ? "~null~" : l.method().value(),
                l.uri() == null ? "~null~" : l.uri().toString(),
                l.content() == null ? "~null~" : "<body>",
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
        try (InputStream in = ElasticsearchInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
