package io.kronikol.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.CorrelationKeys;
import io.kronikol.core.context.TestCorrelationStore;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the two-phase MongoDB recorder matches .NET {@code MongoDbTrackingSubscriber}: started→request,
 * succeeded→response (status OK), failed→response (500), ignored/getMore skips, and write auto-correlation.
 */
class MongoInteractionRecorderTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestCorrelationStore.clear();
    }

    private static MongoDbTrackingOptions.Builder opts() {
        return MongoDbTrackingOptions.builder()
            .serviceName("ShopDb")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1));
    }

    @Test
    void findStartedAndSucceededEmitsPair() {
        MongoInteractionRecorder rec = new MongoInteractionRecorder(opts().build());
        BsonDocument find = new BsonDocument("find", new BsonString("users"))
            .append("filter", new BsonDocument("_id", new BsonInt32(1)));

        rec.logStarted(7, "find", "shop", find);
        BsonDocument reply = new BsonDocument("cursor",
            new BsonDocument("firstBatch", new BsonArray(List.of(new BsonDocument("name", new BsonString("Ada"))))));
        rec.logSucceeded(7, reply);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("Find ← users");
        assertThat(req.uri().toString()).isEqualTo("mongodb:///shop/users");
        assertThat(req.serviceName()).isEqualTo("ShopDb");

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of("OK"));
        assertThat(res.content()).contains("Ada"); // document preview
        assertThat(req.traceId()).isEqualTo(res.traceId());
    }

    @Test
    void ignoredCommandIsSkipped() {
        MongoInteractionRecorder rec = new MongoInteractionRecorder(opts().build());
        rec.logStarted(1, "hello", "admin", new BsonDocument("hello", new BsonInt32(1)));
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void getMoreSkippedByDefault() {
        MongoInteractionRecorder rec = new MongoInteractionRecorder(opts().build());
        rec.logStarted(1, "getMore", "shop", new BsonDocument("getMore", new BsonInt32(5)));
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void failedCommandEmits500() {
        MongoInteractionRecorder rec = new MongoInteractionRecorder(opts().build());
        rec.logStarted(9, "find", "shop", new BsonDocument("find", new BsonString("users")));
        rec.logFailed(9, new RuntimeException("connection reset"));

        RequestResponseLog res = RequestResponseLogger.getAllLogs().get(1);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of(500));
        assertThat(res.content()).isEqualTo("connection reset");
    }

    @Test
    void insertWithIdAutoCorrelatesTheWrite() {
        MongoInteractionRecorder rec = new MongoInteractionRecorder(opts().build());
        // update by _id is a write carrying a document id -> correlation seeded on success
        BsonDocument update = new BsonDocument("update", new BsonString("users"))
            .append("filter", new BsonDocument("_id", new BsonString("u-1")));
        rec.logStarted(3, "update", "shop", update);
        rec.logSucceeded(3, new BsonDocument("nModified", new BsonInt32(1)));

        String key = CorrelationKeys.mongo("ShopDb", "u-1");
        assertThat(TestCorrelationStore.resolve(key)).isEqualTo(new TestInfo("MyTest", "id-1"));
    }
}
