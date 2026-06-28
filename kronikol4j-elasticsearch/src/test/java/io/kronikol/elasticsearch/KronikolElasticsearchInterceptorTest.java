package io.kronikol.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.elasticsearch.ElasticsearchTracking.ElasticsearchTrackingOptions;
import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import java.net.URI;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@link KronikolElasticsearchInterceptor} through hand-built Apache HttpCore objects (no live
 * cluster) to verify it reconstructs the absolute URI from the context's target host + request line and emits
 * the classified Elasticsearch pair — the .NET {@code ElasticsearchTrackingCallbackHandler} behaviour.
 */
class KronikolElasticsearchInterceptorTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static ElasticsearchTrackingOptions opts() {
        return ElasticsearchTrackingOptions.forCluster("Search")
            .withTestInfoFetcher(() -> new TestInfo("MyTest", "id-1"));
    }

    @Test
    void searchRequestIsClassifiedAndRecorded() {
        var interceptor = new KronikolElasticsearchInterceptor(opts());
        var context = HttpCoreContext.create();
        context.setTargetHost(new HttpHost("localhost", 9200, "http"));
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, new BasicHttpRequest("GET", "/orders/_search"));
        var response = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));

        interceptor.process(response, context);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.method().value()).contains("orders"); // classifier directional label includes the index
        assertThat(req.uri().toString()).isEqualTo("elasticsearch:///orders");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.ELASTICSEARCH);
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of(200));
    }

    @Test
    void trackCoreEmitsRealStatus() {
        new KronikolElasticsearchInterceptor(opts())
            .track("DELETE", URI.create("http://localhost:9200/orders/_doc/1"), 404);

        RequestResponseLog res = RequestResponseLogger.getAllLogs().get(1);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of(404)); // real status, not a fixed "OK"
    }
}
