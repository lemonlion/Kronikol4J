package io.kronikol.eventhubs;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.TrackingVerbosity;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the Event Hubs classifier + two-phase recorder match the .NET
 *  {@code EventHubsOperationClassifier} / {@code EventHubsTracker}. */
class EventHubsTrackingTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static EventHubsTrackerOptions.Builder opts() {
        return EventHubsTrackerOptions.builder()
            .serviceName("EventHubs")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1));
    }

    @Test
    void classifiesSendVsSendBatchByEventCount() {
        assertThat(EventHubsOperationClassifier.classify("SendAsync", "orders", null, 1).operation())
            .isEqualTo(EventHubsOperation.SEND);
        assertThat(EventHubsOperationClassifier.classify("SendAsync", "orders", null, 5).operation())
            .isEqualTo(EventHubsOperation.SEND_BATCH);
        assertThat(EventHubsOperationClassifier.classify("ReadEventsFromPartitionAsync", "orders", "3", null)
            .operation()).isEqualTo(EventHubsOperation.READ_EVENTS_FROM_PARTITION);
        assertThat(EventHubsOperationClassifier.classify("Nope", null, null, null).operation())
            .isEqualTo(EventHubsOperation.OTHER);
    }

    @Test
    void diagramLabelsAcrossVerbosity() {
        EventHubsOperationInfo batch = EventHubsOperationClassifier.classify("SendAsync", "orders", null, 5);
        assertThat(EventHubsOperationClassifier.getDiagramLabel(batch, TrackingVerbosity.DETAILED))
            .isEqualTo("Send (×5) → orders");
        assertThat(EventHubsOperationClassifier.getDiagramLabel(batch, TrackingVerbosity.SUMMARISED))
            .isEqualTo("Send");

        EventHubsOperationInfo readPart =
            EventHubsOperationClassifier.classify("ReadEventsFromPartitionAsync", "orders", "3", null);
        assertThat(EventHubsOperationClassifier.getDiagramLabel(readPart, TrackingVerbosity.DETAILED))
            .isEqualTo("Read ← orders[3]");
        assertThat(EventHubsOperationClassifier.getDiagramLabel(readPart, TrackingVerbosity.SUMMARISED))
            .isEqualTo("Read");
    }

    @Test
    void recordsAnEventStyledRequestResponsePairWithPartitionUri() {
        EventHubsInteractionRecorder rec = new EventHubsInteractionRecorder(opts().build());
        EventHubsOperationInfo op =
            EventHubsOperationClassifier.classify("ReadEventsFromPartitionAsync", "orders", "3", null);

        Optional<EventHubsInteractionRecorder.Correlation> corr = rec.logRequest(op, "batch");
        assertThat(corr).isPresent();
        rec.logResponse(op, corr.get(), "10 events");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.metaType()).isEqualTo(RequestResponseMetaType.EVENT);
        assertThat(req.method().value()).isEqualTo("Read ← orders[3]");
        assertThat(req.uri().toString()).isEqualTo("eventhubs:///orders/3");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);

        RequestResponseLog res = logs.get(1);
        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.traceId()).isEqualTo(req.traceId());
        assertThat(res.requestResponseId()).isEqualTo(req.requestResponseId());
    }

    @Test
    void notTrackedWithoutIdentity() {
        EventHubsInteractionRecorder noId = new EventHubsInteractionRecorder(
            EventHubsTrackerOptions.builder().ids(IdGenerator.seeded(1)).build());
        assertThat(noId.logRequest(
            EventHubsOperationClassifier.classify("SendAsync", "orders", null, 1), null)).isEmpty();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void rendersAQueueInteraction() {
        EventHubsInteractionRecorder rec = new EventHubsInteractionRecorder(opts().build());
        rec.logRequest(EventHubsOperationClassifier.classify("SendAsync", "orders", null, 1), null);

        String uml = PlantUmlCreator.create(RequestResponseLogger.getAllLogs()).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("queue \"EventHubs\" as eventHubs")  // MessageQueue category → queue shape
            .contains("Send");
    }
}
