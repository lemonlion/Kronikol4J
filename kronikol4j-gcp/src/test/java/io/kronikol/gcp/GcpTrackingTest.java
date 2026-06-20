package io.kronikol.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.gcp.GcpTracking.GcpTrackingOptions;
import io.kronikol.junit5.KronikolExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class GcpTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void bigQueryRendersAsADatabaseParticipant() {
        GcpTracking.bigQuery(GcpTrackingOptions.forService("Analytics"), "query", "sales",
            "SELECT count(*) FROM orders");
        String uml = PlantUmlCreator.create(RequestResponseLogger.getAllLogs()).get(0).diagrams().get(0);
        assertThat(uml).contains("database \"Analytics\" as Analytics")
            .contains("Test -> Analytics : QUERY /");
    }

    @Test
    void pubSubRendersAsAQueueEvent() {
        GcpTracking.pubSub(GcpTrackingOptions.forService("Events"), "orders", "{\"id\":1}");
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).allSatisfy(l ->
            assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT));
        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("queue \"Events\" as Events").contains("Test -> Events : PUBLISH /");
    }
}
