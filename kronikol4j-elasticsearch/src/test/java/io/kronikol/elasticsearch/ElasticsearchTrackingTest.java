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
            .contains("test -> searchCluster: SEARCH: /");
    }
}
