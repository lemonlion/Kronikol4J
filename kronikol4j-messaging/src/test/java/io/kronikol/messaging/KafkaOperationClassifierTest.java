package io.kronikol.messaging;

import static io.kronikol.core.tracking.TrackingVerbosity.DETAILED;
import static io.kronikol.core.tracking.TrackingVerbosity.RAW;
import static io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies the Kafka diagram-label + URI mapping matches the .NET {@code KafkaOperationClassifier}. */
class KafkaOperationClassifierTest {

    @Test
    void rawLabelIncludesOperationTopicPartitionAndOffset() {
        KafkaOperationInfo op = new KafkaOperationInfo(KafkaOperation.PRODUCE_ASYNC, "orders", 3, 42L);
        assertThat(KafkaOperationClassifier.getDiagramLabel(op, RAW)).isEqualTo("ProduceAsync orders[3]@42");
    }

    @Test
    void rawLabelOmitsAbsentPartitionAndOffsetAndTopic() {
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.CONSUME, "events"), RAW)).isEqualTo("Consume events");
        // Null topic interpolates to empty (trailing space after the operation), matching .NET.
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.FLUSH), RAW)).isEqualTo("Flush ");
    }

    @Test
    void detailedLabelUsesDirectionalArrowsAndFallsBack() {
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.PRODUCE, "orders"), DETAILED)).isEqualTo("Produce → orders");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.CONSUME, "orders"), DETAILED)).isEqualTo("Consume ← orders");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.SUBSCRIBE, "orders"), DETAILED)).isEqualTo("Subscribe orders");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.COMMIT, "orders"), DETAILED)).isEqualTo("Commit"); // fallback
    }

    @Test
    void summarisedLabelsAreTerse() {
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.PRODUCE_ASYNC, "t"), SUMMARISED)).isEqualTo("Produce");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.INIT_TRANSACTIONS), SUMMARISED)).isEqualTo("Init Txn");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.BEGIN_TRANSACTION), SUMMARISED)).isEqualTo("Begin Txn");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.COMMIT_TRANSACTION), SUMMARISED)).isEqualTo("Commit Txn");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.ABORT_TRANSACTION), SUMMARISED)).isEqualTo("Abort Txn");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.SEND_OFFSETS_TO_TRANSACTION), SUMMARISED))
            .isEqualTo("Send Offsets");
        assertThat(KafkaOperationClassifier.getDiagramLabel(
            new KafkaOperationInfo(KafkaOperation.UNSUBSCRIBE), SUMMARISED)).isEqualTo("Unsubscribe"); // fallback
    }

    @Test
    void buildUriMatrix() {
        KafkaOperationInfo full = new KafkaOperationInfo(KafkaOperation.PRODUCE, "orders", 2, 99L);
        assertThat(KafkaOperationClassifier.buildUri(full, RAW)).isEqualTo(URI.create("kafka:///orders/2@99"));
        assertThat(KafkaOperationClassifier.buildUri(
            new KafkaOperationInfo(KafkaOperation.PRODUCE, "orders"), RAW)).isEqualTo(URI.create("kafka:///orders"));
        assertThat(KafkaOperationClassifier.buildUri(full, DETAILED)).isEqualTo(URI.create("kafka:///orders"));
        // Summarised (and topic-less) always collapse to the bare scheme.
        assertThat(KafkaOperationClassifier.buildUri(full, SUMMARISED)).isEqualTo(URI.create("kafka:///"));
        assertThat(KafkaOperationClassifier.buildUri(
            new KafkaOperationInfo(KafkaOperation.FLUSH), RAW)).isEqualTo(URI.create("kafka:///"));
    }
}
