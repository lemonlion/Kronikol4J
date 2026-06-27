package io.kronikol.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

/**
 * Verifies the MongoDB command classification + metadata extraction + diagram labels match the .NET
 * {@code MongoDbOperationClassifier}. Pure logic over BSON, so a unit test is the proof.
 */
class MongoDbOperationClassifierTest {

    private static BsonDocument find(String collection) {
        return new BsonDocument("find", new BsonString(collection))
            .append("filter", new BsonDocument("_id", new BsonInt32(1)));
    }

    @Test
    void classifiesCommonCommands() {
        assertThat(MongoDbOperationClassifier.classify("find", "db", find("users")).operation())
            .isEqualTo(MongoDbOperation.FIND);
        assertThat(MongoDbOperationClassifier.classify("insert", "db",
            new BsonDocument("insert", new BsonString("users"))).operation())
            .isEqualTo(MongoDbOperation.INSERT);
        assertThat(MongoDbOperationClassifier.classify("findandmodify", "db", null).operation())
            .isEqualTo(MongoDbOperation.FIND_AND_MODIFY);
        assertThat(MongoDbOperationClassifier.classify("countdocuments", "db", null).operation())
            .isEqualTo(MongoDbOperation.COUNT);
        assertThat(MongoDbOperationClassifier.classify("weird", "db", null).operation())
            .isEqualTo(MongoDbOperation.OTHER);
    }

    @Test
    void aggregateWithChangeStreamBecomesWatch() {
        BsonArray pipeline = new BsonArray();
        pipeline.add(new BsonDocument("$changeStream", new BsonDocument()));
        BsonDocument cmd = new BsonDocument("aggregate", new BsonString("orders")).append("pipeline", pipeline);
        assertThat(MongoDbOperationClassifier.classify("aggregate", "db", cmd).operation())
            .isEqualTo(MongoDbOperation.WATCH);
    }

    @Test
    void extractsCollectionFilterAndDocumentId() {
        MongoDbOperationInfo info = MongoDbOperationClassifier.classify("find", "shop", find("users"));
        assertThat(info.collectionName()).isEqualTo("users");
        assertThat(info.filterText()).contains("_id");
        assertThat(info.documentId()).contains("1");
    }

    @Test
    void extractsInsertDocumentCount() {
        BsonArray docs = new BsonArray();
        docs.add(new BsonDocument("a", new BsonInt32(1)));
        docs.add(new BsonDocument("a", new BsonInt32(2)));
        BsonDocument cmd = new BsonDocument("insert", new BsonString("users")).append("documents", docs);
        assertThat(MongoDbOperationClassifier.classify("insert", "db", cmd).documentCount()).isEqualTo(2);
    }

    @Test
    void extractsPipelineStages() {
        BsonArray pipeline = new BsonArray();
        pipeline.add(new BsonDocument("$match", new BsonDocument()));
        pipeline.add(new BsonDocument("$group", new BsonDocument()));
        BsonDocument cmd = new BsonDocument("aggregate", new BsonString("orders")).append("pipeline", pipeline);
        assertThat(MongoDbOperationClassifier.classify("aggregate", "db", cmd).pipelineStages())
            .isEqualTo("$match, $group");
    }

    @Test
    void detectsGridFs() {
        BsonDocument cmd = new BsonDocument("find", new BsonString("fs.files"));
        assertThat(MongoDbOperationClassifier.classify("find", "db", cmd).isGridFs()).isTrue();
    }

    @Test
    void detailedLabelHasDirectionalArrowAndAnnotations() {
        // insert of 3 docs into users -> "Insert (×3) → users"
        BsonArray docs = new BsonArray();
        for (int i = 0; i < 3; i++) {
            docs.add(new BsonDocument("a", new BsonInt32(i)));
        }
        BsonDocument cmd = new BsonDocument("insert", new BsonString("users")).append("documents", docs);
        MongoDbOperationInfo insert = MongoDbOperationClassifier.classify("insert", "db", cmd);
        assertThat(MongoDbOperationClassifier.getDiagramLabel(insert, TrackingVerbosity.DETAILED))
            .isEqualTo("Insert (×3) → users");

        MongoDbOperationInfo findInfo = MongoDbOperationClassifier.classify("find", "db", find("users"));
        assertThat(MongoDbOperationClassifier.getDiagramLabel(findInfo, TrackingVerbosity.DETAILED))
            .isEqualTo("Find ← users"); // read arrow
        assertThat(MongoDbOperationClassifier.getDiagramLabel(findInfo, TrackingVerbosity.SUMMARISED))
            .isEqualTo("Find");
    }
}
