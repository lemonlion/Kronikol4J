package io.kronikol.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies the {@link AtlasDataApiOperationClassifier} matches the .NET classifier — action-path mapping,
 *  body field extraction, and directional-arrow labels. */
class AtlasDataApiOperationClassifierTest {

    private static final String ENDPOINT =
        "https://data.mongodb-api.com/app/myapp/endpoint/data/v1/action/";

    @Test
    void mapsActionPathToOperations() {
        assertThat(classify("findOne", null).operation()).isEqualTo(AtlasDataApiOperation.FIND_ONE);
        assertThat(classify("insertMany", null).operation()).isEqualTo(AtlasDataApiOperation.INSERT_MANY);
        assertThat(classify("updateOne", null).operation()).isEqualTo(AtlasDataApiOperation.UPDATE_ONE);
        assertThat(classify("aggregate", null).operation()).isEqualTo(AtlasDataApiOperation.AGGREGATE);
        // unknown action / non-action path → Other
        assertThat(classify("frobnicate", null).operation()).isEqualTo(AtlasDataApiOperation.OTHER);
        assertThat(AtlasDataApiOperationClassifier.classify(URI.create("https://x/health"), null).operation())
            .isEqualTo(AtlasDataApiOperation.OTHER);
    }

    @Test
    void extractsBodyMetadata() {
        String body = "{\"dataSource\":\"Cluster0\",\"database\":\"shop\",\"collection\":\"orders\","
            + "\"filter\":{\"status\":\"open\",\"qty\":{\"$gt\":3}}}";
        AtlasDataApiOperationInfo op = classify("find", body);

        assertThat(op.operation()).isEqualTo(AtlasDataApiOperation.FIND);
        assertThat(op.dataSource()).isEqualTo("Cluster0");
        assertThat(op.databaseName()).isEqualTo("shop");
        assertThat(op.collectionName()).isEqualTo("orders");
        assertThat(op.filterText()).isEqualTo("{\"status\":\"open\",\"qty\":{\"$gt\":3}}"); // balanced object
    }

    @Test
    void directionalArrowLabels() {
        AtlasDataApiOperationInfo find = classify("find",
            "{\"collection\":\"orders\"}");
        assertThat(AtlasDataApiOperationClassifier.getDiagramLabel(find, TrackingVerbosity.DETAILED))
            .isEqualTo("Find ← orders");

        AtlasDataApiOperationInfo insert = classify("insertOne", "{\"collection\":\"orders\"}");
        assertThat(AtlasDataApiOperationClassifier.getDiagramLabel(insert, TrackingVerbosity.DETAILED))
            .isEqualTo("InsertOne → orders");

        AtlasDataApiOperationInfo update = classify("updateMany", "{\"collection\":\"orders\"}");
        assertThat(AtlasDataApiOperationClassifier.getDiagramLabel(update, TrackingVerbosity.DETAILED))
            .isEqualTo("UpdateMany ↔ orders");

        // no collection → bare operation name; Summarised always bare
        assertThat(AtlasDataApiOperationClassifier.getDiagramLabel(classify("find", null),
            TrackingVerbosity.DETAILED)).isEqualTo("Find");
        assertThat(AtlasDataApiOperationClassifier.getDiagramLabel(update, TrackingVerbosity.SUMMARISED))
            .isEqualTo("UpdateMany");
    }

    private static AtlasDataApiOperationInfo classify(String action, String body) {
        return AtlasDataApiOperationClassifier.classify(URI.create(ENDPOINT + action), body);
    }
}
