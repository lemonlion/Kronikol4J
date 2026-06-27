package io.kronikol.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the injectable MessageTracker emits the request/response pairs matching .NET {@code MessageTracker}
 * for each distinct tracking method (send-event, send-message, consume-event, manual request/response).
 */
class MessageTrackerTest {

    private static final URI TOPIC = URI.create("kafka:///orders");

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static MessageTracker tracker() {
        return new MessageTracker(MessageTrackerOptions.builder()
            .serviceName("orders-topic")
            .callerName("Producer")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build());
    }

    @Test
    void trackSendEventEmitsEventStyledPair() {
        tracker().trackSendEvent("Kafka", "orders-topic", TOPIC, Map.of("id", 1));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("Kafka");
        assertThat(req.serviceName()).isEqualTo("orders-topic");
        assertThat(req.callerName()).isEqualTo("Producer");
        assertThat(req.metaType()).isEqualTo(RequestResponseMetaType.EVENT);
        assertThat(req.content()).contains("\"id\"").contains("1");
        assertThat(req.requestResponseId()).isEqualTo(res.requestResponseId());

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isEqualTo(StatusCode.of("Responded"));
        assertThat(res.content()).isEmpty(); // no response payload -> empty string
    }

    @Test
    void trackSendMessageUsesSentAckAndNullResponseBody() {
        UUID id = tracker().trackSendMessage("Kafka", "orders-topic", TOPIC, Map.of("a", 1));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(id).isNotNull();
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of("Sent"));
        assertThat(logs.get(1).content()).isNull(); // send-message response carries no content
    }

    @Test
    void trackConsumeEventPlacesNoteOnRightAndUsesAckLabel() {
        tracker().trackConsumeEvent("Consume (Kafka)", "order-service", TOPIC, Map.of("id", 2));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).noteOnRight()).isTrue();
        assertThat(logs.get(0).serviceName()).isEqualTo("order-service");
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of("Ack"));
    }

    @Test
    void manualRequestResponsePairing() {
        MessageTracker t = tracker();
        UUID id = t.trackMessageRequest("Kafka", "orders-topic", TOPIC, Map.of("id", 9), false);
        assertThat(id).isNotNull();
        t.trackMessageResponse("Kafka", "orders-topic", TOPIC, id, Map.of("ack", true), "Delivered");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).requestResponseId()).isEqualTo(logs.get(1).requestResponseId());
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of("Delivered"));
        assertThat(logs.get(1).content()).contains("\"ack\"");
    }

    @Test
    void noTestContextEmitsNothing() {
        MessageTracker t = new MessageTracker(MessageTrackerOptions.builder()
            .testInfoFetcher(() -> null).ids(IdGenerator.seeded(1)).build());
        assertThat(t.trackMessageRequest("Kafka", "t", TOPIC, Map.of(), false)).isNull();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
