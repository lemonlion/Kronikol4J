package io.kronikol.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import io.kronikol.messaging.MessageTracking.MessageTrackingOptions;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class MessageTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void recordsPublishAsAnEventToAQueueParticipant() {
        MessageTracking.publish(MessageTrackingOptions.forBroker("Kafka"), "orders", "{\"id\":42}");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(l ->
            assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT)); // fire-and-forget
        assertThat(logs.get(0).content()).contains("topic: orders");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("queue \"Kafka\" as kafka")
            .contains("test -[#9B59B6]> kafka: PUBLISH: /");
    }
}
