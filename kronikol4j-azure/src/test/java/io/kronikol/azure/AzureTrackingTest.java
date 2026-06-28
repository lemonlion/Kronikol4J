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
            .contains("test -[#E74C3C]> ordersDb: UPSERT: /");
    }

    @Test
    void serviceBusRendersAsAQueueEvent() {
        AzureTracking.serviceBus(AzureTrackingOptions.forService("Bus"), "orders", "{\"id\":1}");
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).allSatisfy(l ->
            assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT));
        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("queue \"Bus\" as bus").contains("test -[#9B59B6]> bus: SEND: /");
    }

    @Test
    void setupPhaseVerbosityOverrideDropsCosmosDocument() {
        var options = AzureTrackingOptions.forService("OrdersDb")
            .withSetupVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.SETUP);
        try {
            AzureTracking.cosmos(options, "Upsert", "orders", "{\"id\":1}");
            assertThat(RequestResponseLogger.getAllLogs().get(0).content()).isEqualTo("orders: ");
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void actionPhaseSuppressionSkipsRecording() {
        var options = AzureTrackingOptions.forService("OrdersDb").withTrackDuringAction(false);
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.ACTION);
        try {
            AzureTracking.cosmos(options, "Upsert", "orders", "{}");
            AzureTracking.blob(options, "PUT", "files", "a.txt");
            AzureTracking.serviceBus(options, "orders", "msg");
            assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void summarisedVerbosityOmitsCosmosDocumentButKeepsContainer() {
        var options = AzureTrackingOptions.forService("OrdersDb")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        AzureTracking.cosmos(options, "Upsert", "orders", "{\"id\":1}");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).content()).isEqualTo("orders: "); // container kept, document dropped
    }

    @Test
    void summarisedVerbosityOmitsServiceBusMessageButKeepsEntity() {
        var options = AzureTrackingOptions.forService("Bus")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        AzureTracking.serviceBus(options, "orders", "{\"id\":1}");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).anySatisfy(l -> assertThat(l.content()).isEqualTo("entity: orders\n"));
        assertThat(logs).noneSatisfy(l -> assertThat(l.content()).contains("{\"id\":1}"));
    }
}
