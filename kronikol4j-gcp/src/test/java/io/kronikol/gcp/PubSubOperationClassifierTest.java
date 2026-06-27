package io.kronikol.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/** Verifies Pub/Sub classification (method name + batch count) + short-name labels match the .NET classifier. */
class PubSubOperationClassifierTest {

    private static final String TOPIC = "projects/p/topics/orders";
    private static final String SUB = "projects/p/subscriptions/orders-sub";

    @Test
    void publishSingleVsBatch() {
        assertThat(PubSubOperationClassifier.classify("PublishAsync", TOPIC, null, 1).operation())
            .isEqualTo(PubSubOperation.PUBLISH);
        assertThat(PubSubOperationClassifier.classify("PublishAsync", TOPIC, null, 3).operation())
            .isEqualTo(PubSubOperation.PUBLISH_BATCH);
        assertThat(PubSubOperationClassifier.classify("PublishAsync", TOPIC, null, null).operation())
            .isEqualTo(PubSubOperation.PUBLISH);
    }

    @Test
    void classifiesEachMethod() {
        assertThat(op("PullAsync")).isEqualTo(PubSubOperation.PULL);
        assertThat(op("AcknowledgeAsync")).isEqualTo(PubSubOperation.ACKNOWLEDGE);
        assertThat(op("ModifyAckDeadlineAsync")).isEqualTo(PubSubOperation.MODIFY_ACK_DEADLINE);
        assertThat(op("Receive")).isEqualTo(PubSubOperation.RECEIVE);
        assertThat(op("StartAsync")).isEqualTo(PubSubOperation.START_SUBSCRIBER);
        assertThat(op("StopAsync")).isEqualTo(PubSubOperation.STOP_SUBSCRIBER);
        assertThat(op("FooAsync")).isEqualTo(PubSubOperation.OTHER);
    }

    @Test
    void detailedLabelsUseShortNamesAndArrows() {
        assertThat(label("PublishAsync", TOPIC, null, 1, TrackingVerbosity.DETAILED)).isEqualTo("Publish → orders");
        assertThat(label("PublishAsync", TOPIC, null, 4, TrackingVerbosity.DETAILED))
            .isEqualTo("Publish (×4) → orders");
        assertThat(label("PullAsync", null, SUB, null, TrackingVerbosity.DETAILED)).isEqualTo("Pull ← orders-sub");
        assertThat(label("Receive", null, SUB, null, TrackingVerbosity.DETAILED)).isEqualTo("Receive ← orders-sub");
        assertThat(label("AcknowledgeAsync", null, SUB, null, TrackingVerbosity.DETAILED)).isEqualTo("Ack");
    }

    @Test
    void summarisedCollapsesBatchPublish() {
        assertThat(label("PublishAsync", TOPIC, null, 4, TrackingVerbosity.SUMMARISED)).isEqualTo("Publish");
        assertThat(label("PullAsync", null, SUB, null, TrackingVerbosity.SUMMARISED)).isEqualTo("Pull");
    }

    private static PubSubOperation op(String method) {
        return PubSubOperationClassifier.classify(method, TOPIC, SUB, null).operation();
    }

    private static String label(String method, String topic, String sub, Integer count, TrackingVerbosity v) {
        return PubSubOperationClassifier.getDiagramLabel(
            PubSubOperationClassifier.classify(method, topic, sub, count), v);
    }
}
