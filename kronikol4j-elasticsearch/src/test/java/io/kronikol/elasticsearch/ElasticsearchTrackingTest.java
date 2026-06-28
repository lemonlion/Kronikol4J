package io.kronikol.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.elasticsearch.ElasticsearchTracking.ElasticsearchTrackingOptions;
import io.kronikol.junit5.KronikolExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class ElasticsearchTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void recordsASearchAsADatabaseParticipant() {
        ElasticsearchTracking.record(ElasticsearchTrackingOptions.forCluster("SearchCluster"),
            "search", "products", "{\"query\":{\"match\":{\"name\":\"egg\"}}}", "3 hits");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("SEARCH");
        assertThat(logs.get(0).content()).contains("products");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("database \"SearchCluster\" as searchCluster")
            .contains("test -[#E74C3C]> searchCluster: SEARCH: /");
    }

    @Test
    void actionPhaseSuppressionSkipsRecording() {
        var options = ElasticsearchTrackingOptions.forCluster("SearchCluster").withTrackDuringAction(false);
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.ACTION);
        try {
            ElasticsearchTracking.record(options, "search", "products", "{}", "0 hits");
            ElasticsearchTracking.record(options, "GET",
                java.net.URI.create("http://es:9200/products/_search"), "{}", "0 hits");
            assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void classifierDrivenRecordUsesClassifierLabelUriAndBody() {
        ElasticsearchTracking.record(ElasticsearchTrackingOptions.forCluster("SearchCluster"),
            "GET", java.net.URI.create("http://es:9200/products/_search"), "{\"query\":{}}", "3 hits");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("Search → products"); // classifier Detailed label
        assertThat(logs.get(0).uri().toString()).isEqualTo("elasticsearch:///products");
        assertThat(logs.get(0).content()).isEqualTo("{\"query\":{}}");
        assertThat(logs.get(1).content()).isEqualTo("3 hits");
    }

    @Test
    void summarisedVerbosityOmitsBodyAndUsesSchemeOnlyUri() {
        var options = ElasticsearchTrackingOptions.forCluster("SearchCluster")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        ElasticsearchTracking.record(options,
            "GET", java.net.URI.create("http://es:9200/products/_search"), "{\"query\":{}}", "3 hits");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).method().value()).isEqualTo("Search");        // terse summarised label
        assertThat(logs.get(0).uri().toString()).isEqualTo("elasticsearch:///");
        assertThat(logs.get(0).content()).isNull();                          // body omitted at Summarised
    }
}
