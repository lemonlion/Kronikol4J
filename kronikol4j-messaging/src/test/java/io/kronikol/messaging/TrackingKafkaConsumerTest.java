package io.kronikol.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the tracking Kafka consumer reads the test-identity headers on each polled record and records a
 * consume event attributed to that test — the receiving side of cross-service correlation. Uses
 * {@link MockConsumer}, so no broker is needed.
 */
class TrackingKafkaConsumerTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        TestIdentityScope.clear();
    }

    /** Tracker WITHOUT a fetcher — identity must come from the scope the consumer opens from headers. */
    private static MessageTracker tracker() {
        return new MessageTracker(MessageTrackerOptions.builder().ids(IdGenerator.seeded(1)).build());
    }

    private static ConsumerRecord<String, String> recordWithIdentity(long offset, String value, TestInfo who) {
        RecordHeaders headers = new RecordHeaders();
        if (who != null) {
            KafkaTestHeaders.stamp(headers, who);
        }
        return new ConsumerRecord<>("orders", 0, offset, 0L,
            org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, "key", value, headers, java.util.Optional.empty());
    }

    private static MockConsumer<String, String> mockWith(ConsumerRecord<String, String>... records) {
        MockConsumer<String, String> mock = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition tp = new TopicPartition("orders", 0);
        mock.assign(List.of(tp));
        mock.updateBeginningOffsets(Map.of(tp, 0L));
        for (ConsumerRecord<String, String> r : records) {
            mock.addRecord(r);
        }
        return mock;
    }

    @Test
    void polledRecordWithIdentityIsTrackedAsConsumeEvent() {
        MockConsumer<String, String> mock =
            mockWith(recordWithIdentity(0L, "{\"id\":1}", new TestInfo("MyTest", "id-1")));
        Consumer<String, String> tracked = TrackingKafkaConsumer.wrap(mock, tracker(), "order-service");

        tracked.poll(Duration.ofMillis(0));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2); // consume event = delivery + ack pair
        RequestResponseLog delivery = logs.get(0);
        RequestResponseLog ack = logs.get(1);

        assertThat(delivery.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(delivery.testName()).isEqualTo("MyTest"); // attributed via the header identity
        assertThat(delivery.serviceName()).isEqualTo("order-service");
        assertThat(delivery.noteOnRight()).isTrue();
        assertThat(delivery.uri().toString()).isEqualTo("kafka:///orders");

        assertThat(ack.statusCode()).isEqualTo(StatusCode.of("Ack"));

        // scope is cleared after the consume event (no leakage onto the polling thread)
        assertThat(TestIdentityScope.current()).isNull();
    }

    @Test
    void recordWithoutIdentityIsNotTracked() {
        MockConsumer<String, String> mock = mockWith(recordWithIdentity(0L, "v", null));
        Consumer<String, String> tracked = TrackingKafkaConsumer.wrap(mock, tracker(), "order-service");

        tracked.poll(Duration.ofMillis(0));
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void pollStillReturnsTheRecordsToTheCaller() {
        MockConsumer<String, String> mock =
            mockWith(recordWithIdentity(0L, "v", new TestInfo("MyTest", "id-1")));
        Consumer<String, String> tracked = TrackingKafkaConsumer.wrap(mock, tracker(), "order-service");

        var records = tracked.poll(Duration.ofMillis(0));
        assertThat(records.count()).isEqualTo(1);
    }
}
