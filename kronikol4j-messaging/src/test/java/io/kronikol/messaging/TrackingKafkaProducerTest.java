package io.kronikol.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.util.List;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the tracking Kafka producer stamps the test-identity headers (the cross-service correlation
 * enabler) and records each send, using {@link MockProducer} so no broker is needed.
 */
class TrackingKafkaProducerTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static MessageTracker tracker() {
        return new MessageTracker(MessageTrackerOptions.builder()
            .serviceName("orders-topic")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build());
    }

    @Test
    void headerStampReadRoundTrip() {
        RecordHeaders headers = new RecordHeaders();
        KafkaTestHeaders.stamp(headers, new TestInfo("T", "i"));
        assertThat(KafkaTestHeaders.read(headers)).isEqualTo(new TestInfo("T", "i"));
        // re-stamp replaces rather than duplicates
        KafkaTestHeaders.stamp(headers, new TestInfo("T2", "i2"));
        assertThat(KafkaTestHeaders.read(headers)).isEqualTo(new TestInfo("T2", "i2"));
    }

    @Test
    void sendStampsHeadersAndTracksThePair() {
        MockProducer<String, String> mock =
            new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        Producer<String, String> tracked =
            TrackingKafkaProducer.wrap(mock, tracker(), () -> new TestInfo("MyTest", "id-1"));

        ProducerRecord<String, String> record = new ProducerRecord<>("orders", "key", "{\"id\":1}");
        tracked.send(record);

        // identity stamped on the outgoing record (visible to a downstream consumer)
        assertThat(KafkaTestHeaders.read(record.headers())).isEqualTo(new TestInfo("MyTest", "id-1"));
        // the real producer received it
        assertThat(mock.history()).hasSize(1);
        assertThat(mock.history().get(0).topic()).isEqualTo("orders");

        // the send was tracked as a request/response pair
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(logs.get(0).method().value()).isEqualTo("Produce → orders"); // classifier Detailed label
        assertThat(logs.get(0).serviceName()).isEqualTo("orders"); // destination = the topic
        assertThat(logs.get(0).uri().toString()).isEqualTo("kafka:///orders");
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of("Sent"));
    }

    @Test
    void sendWithoutTestContextStillDelegatesButDoesNotStampOrTrack() {
        MockProducer<String, String> mock =
            new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        MessageTracker noCtx = new MessageTracker(MessageTrackerOptions.builder()
            .testInfoFetcher(() -> null).ids(IdGenerator.seeded(1)).build());
        Producer<String, String> tracked = TrackingKafkaProducer.wrap(mock, noCtx, () -> null);

        ProducerRecord<String, String> record = new ProducerRecord<>("orders", "v");
        tracked.send(record);

        assertThat(KafkaTestHeaders.read(record.headers())).isNull();
        assertThat(mock.history()).hasSize(1); // message still sent
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
