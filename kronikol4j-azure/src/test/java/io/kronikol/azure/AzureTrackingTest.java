package io.kronikol.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.azure.AzureTracking.AzureTrackingOptions;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class AzureTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void cosmosRendersAsADatabaseParticipant() {
        AzureTracking.cosmos(AzureTrackingOptions.forService("OrdersDb"), "Upsert", "orders", "{\"id\":1}");
        String uml = PlantUmlCreator.create(RequestResponseLogger.getAllLogs()).get(0).diagrams().get(0);
        assertThat(uml).contains("database \"OrdersDb\" as ordersDb")
            .contains("test -> ordersDb: UPSERT: /");
    }

    @Test
    void serviceBusRendersAsAQueueEvent() {
        AzureTracking.serviceBus(AzureTrackingOptions.forService("Bus"), "orders", "{\"id\":1}");
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).allSatisfy(l ->
            assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT));
        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("queue \"Bus\" as bus").contains("test -> bus: SEND: /");
    }
}
