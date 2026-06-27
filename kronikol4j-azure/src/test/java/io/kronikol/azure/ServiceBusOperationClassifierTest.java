package io.kronikol.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/** Verifies Service Bus classification (SDK method name → operation) + labels match the .NET classifier. */
class ServiceBusOperationClassifierTest {

    @Test
    void classifiesMethodNames() {
        assertThat(op("SendMessageAsync")).isEqualTo(ServiceBusOperation.SEND);
        assertThat(op("SendMessagesAsync")).isEqualTo(ServiceBusOperation.SEND_BATCH);
        assertThat(op("ScheduleMessagesAsync")).isEqualTo(ServiceBusOperation.SCHEDULE);
        assertThat(op("CancelScheduledMessageAsync")).isEqualTo(ServiceBusOperation.CANCEL_SCHEDULE);
        assertThat(op("ReceiveMessageAsync")).isEqualTo(ServiceBusOperation.RECEIVE);
        assertThat(op("ReceiveMessagesAsync")).isEqualTo(ServiceBusOperation.RECEIVE_BATCH);
        assertThat(op("PeekMessagesAsync")).isEqualTo(ServiceBusOperation.PEEK);
        assertThat(op("DeadLetterMessageAsync")).isEqualTo(ServiceBusOperation.DEAD_LETTER);
        assertThat(op("RenewSessionLockAsync")).isEqualTo(ServiceBusOperation.RENEW_SESSION_LOCK);
        assertThat(op("StopProcessingAsync")).isEqualTo(ServiceBusOperation.STOP_PROCESSING);
        assertThat(op("WhoKnowsAsync")).isEqualTo(ServiceBusOperation.OTHER);
    }

    @Test
    void carriesEntityPathAndCount() {
        ServiceBusOperationInfo info =
            ServiceBusOperationClassifier.classify("SendMessagesAsync", "orders-queue", 5);
        assertThat(info.queueOrTopicName()).isEqualTo("orders-queue");
        assertThat(info.messageCount()).isEqualTo(5);
    }

    @Test
    void detailedLabelsWithArrowsAndCounts() {
        assertThat(label("SendMessageAsync", "q", null, TrackingVerbosity.DETAILED)).isEqualTo("Send → q");
        assertThat(label("SendMessagesAsync", "q", 3, TrackingVerbosity.DETAILED)).isEqualTo("Send (×3) → q");
        assertThat(label("SendMessagesAsync", "q", null, TrackingVerbosity.DETAILED)).isEqualTo("Send (batch) → q");
        assertThat(label("ReceiveMessageAsync", "q", null, TrackingVerbosity.DETAILED)).isEqualTo("Receive ← q");
        assertThat(label("ReceiveMessagesAsync", "q", 2, TrackingVerbosity.DETAILED)).isEqualTo("Receive (×2) ← q");
        assertThat(label("PeekMessageAsync", "q", null, TrackingVerbosity.DETAILED)).isEqualTo("Peek ← q");
    }

    @Test
    void summarisedCollapsesBatchesAndRaw() {
        assertThat(label("SendMessagesAsync", "q", 3, TrackingVerbosity.SUMMARISED)).isEqualTo("Send");
        assertThat(label("ReceiveMessagesAsync", "q", 3, TrackingVerbosity.SUMMARISED)).isEqualTo("Receive");
        assertThat(label("RenewMessageLockAsync", "q", null, TrackingVerbosity.SUMMARISED)).isEqualTo("RenewLock");
        assertThat(label("SendMessageAsync", "q", null, TrackingVerbosity.RAW)).isEqualTo("Send");
    }

    private static ServiceBusOperation op(String method) {
        return ServiceBusOperationClassifier.classify(method, "q", null).operation();
    }

    private static String label(String method, String entity, Integer count, TrackingVerbosity v) {
        return ServiceBusOperationClassifier.getDiagramLabel(
            ServiceBusOperationClassifier.classify(method, entity, count), v);
    }
}
