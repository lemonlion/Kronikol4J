package io.kronikol.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Elasticsearch request classification (HTTP method + path → operation) + labels match the .NET
 * {@code ElasticsearchOperationClassifier}. Pure logic, so a unit test is the proof.
 */
class ElasticsearchOperationClassifierTest {

    private static ElasticsearchOperationInfo classify(String method, String path) {
        return ElasticsearchOperationClassifier.classify(method, URI.create("http://es:9200" + path));
    }

    @Test
    void documentWithId() {
        assertThat(classify("PUT", "/books/_doc/1").operation()).isEqualTo(ElasticsearchOperation.INDEX_DOCUMENT);
        assertThat(classify("GET", "/books/_doc/1").operation()).isEqualTo(ElasticsearchOperation.GET_DOCUMENT);
        assertThat(classify("DELETE", "/books/_doc/1").operation()).isEqualTo(ElasticsearchOperation.DELETE_DOCUMENT);

        ElasticsearchOperationInfo info = classify("GET", "/books/_doc/42");
        assertThat(info.indexName()).isEqualTo("books");
        assertThat(info.documentId()).isEqualTo("42");
    }

    @Test
    void documentWithoutIdAndUpdate() {
        assertThat(classify("POST", "/books/_doc").operation()).isEqualTo(ElasticsearchOperation.INDEX_DOCUMENT);
        assertThat(classify("POST", "/books/_update/7").operation()).isEqualTo(ElasticsearchOperation.UPDATE_DOCUMENT);
        assertThat(classify("POST", "/books/_update/7").documentId()).isEqualTo("7");
    }

    @Test
    void indexLevelActions() {
        assertThat(classify("POST", "/books/_search").operation()).isEqualTo(ElasticsearchOperation.SEARCH);
        assertThat(classify("GET", "/books/_count").operation()).isEqualTo(ElasticsearchOperation.COUNT);
        assertThat(classify("POST", "/books/_delete_by_query").operation())
            .isEqualTo(ElasticsearchOperation.DELETE_BY_QUERY);
        assertThat(classify("PUT", "/books/_mapping").operation()).isEqualTo(ElasticsearchOperation.PUT_MAPPING);
        assertThat(classify("GET", "/books/_mapping").operation()).isEqualTo(ElasticsearchOperation.GET_MAPPING);
        assertThat(classify("POST", "/books/_refresh").operation()).isEqualTo(ElasticsearchOperation.REFRESH);
    }

    @Test
    void clusterAndGlobalOperations() {
        assertThat(classify("GET", "/_cluster/health").operation()).isEqualTo(ElasticsearchOperation.CLUSTER_HEALTH);
        assertThat(classify("GET", "/_cat/indices").operation()).isEqualTo(ElasticsearchOperation.CAT_APIS);
        assertThat(classify("POST", "/_msearch").operation()).isEqualTo(ElasticsearchOperation.MULTI_SEARCH);
        assertThat(classify("POST", "/_bulk").operation()).isEqualTo(ElasticsearchOperation.BULK);
        assertThat(classify("POST", "/_reindex").operation()).isEqualTo(ElasticsearchOperation.REINDEX);
        assertThat(classify("PUT", "/_index_template/logs").operation())
            .isEqualTo(ElasticsearchOperation.PUT_INDEX_TEMPLATE);
    }

    @Test
    void indexOnlyOperations() {
        assertThat(classify("PUT", "/books").operation()).isEqualTo(ElasticsearchOperation.CREATE_INDEX);
        assertThat(classify("DELETE", "/books").operation()).isEqualTo(ElasticsearchOperation.DELETE_INDEX);
        assertThat(classify("HEAD", "/books").operation()).isEqualTo(ElasticsearchOperation.INDEX_EXISTS);
    }

    @Test
    void diagramLabelsAndUri() {
        ElasticsearchOperationInfo index = classify("PUT", "/books/_doc/1");
        assertThat(ElasticsearchOperationClassifier.getDiagramLabel(index, TrackingVerbosity.DETAILED))
            .isEqualTo("Index → books");
        assertThat(ElasticsearchOperationClassifier.getDiagramLabel(index, TrackingVerbosity.SUMMARISED))
            .isEqualTo("Index");

        ElasticsearchOperationInfo search = classify("POST", "/books/_search");
        assertThat(ElasticsearchOperationClassifier.getDiagramLabel(search, TrackingVerbosity.DETAILED))
            .isEqualTo("Search → books");
        assertThat(ElasticsearchOperationClassifier.buildUri(search, TrackingVerbosity.DETAILED, null).toString())
            .isEqualTo("elasticsearch:///books");
        assertThat(ElasticsearchOperationClassifier.buildUri(search, TrackingVerbosity.SUMMARISED, null).toString())
            .isEqualTo("elasticsearch:///");
    }
}
