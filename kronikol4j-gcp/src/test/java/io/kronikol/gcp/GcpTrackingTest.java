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
        assertThat(uml).contains("database \"Analytics\" as analytics")
            .contains("test -[#E74C3C]> analytics: QUERY: /");
    }

    @Test
    void pubSubRendersAsAQueueEvent() {
        GcpTracking.pubSub(GcpTrackingOptions.forService("Events"), "orders", "{\"id\":1}");
        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).allSatisfy(l ->
            assertThat(l.metaType()).isEqualTo(RequestResponseMetaType.EVENT));
        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml).contains("queue \"Events\" as events").contains("test -[#9B59B6]> events: PUBLISH: /");
    }

    @Test
    void actionPhaseSuppressionSkipsRecording() {
        var options = GcpTrackingOptions.forService("Analytics").withTrackDuringAction(false);
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.ACTION);
        try {
            GcpTracking.bigQuery(options, "query", "sales", "SELECT 1");
            GcpTracking.storage(options, "GET", "bucket", "obj");
            GcpTracking.pubSub(options, "orders", "msg");
            assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void summarisedVerbosityOmitsBigQueryQueryButKeepsDataset() {
        var options = GcpTrackingOptions.forService("Analytics")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        GcpTracking.bigQuery(options, "query", "sales", "SELECT count(*) FROM orders");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).content()).isEqualTo("sales: "); // dataset kept, query dropped
    }

    @Test
    void summarisedVerbosityOmitsPubSubMessageButKeepsTopic() {
        var options = GcpTrackingOptions.forService("Events")
            .withVerbosity(io.kronikol.core.tracking.TrackingVerbosity.SUMMARISED);
        GcpTracking.pubSub(options, "orders", "{\"id\":1}");

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).anySatisfy(l -> assertThat(l.content()).isEqualTo("topic: orders\n"));
        assertThat(logs).noneSatisfy(l -> assertThat(l.content()).contains("{\"id\":1}"));
    }
}
