package io.kronikol.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the in-process message-bus classifier + recorder match the .NET
 *  {@code MassTransitOperationClassifier} / {@code MassTransitTracker}. */
class EventBusTrackingTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static EventBusTrackerOptions.Builder opts() {
        return EventBusTrackerOptions.builder()
            .serviceName("OrderBus").callerName("Test")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1));
    }

    // --- classifier ---

    @Test
    void labelsAcrossVerbosity() {
        EventBusOperationInfo send = EventBusOperationClassifier.classifySend(
            "OrderPlaced", URI.create("rabbitmq://localhost/orders-queue"), null, "m1", "c1");
        assertThat(EventBusOperationClassifier.getDiagramLabel(send, TrackingVerbosity.DETAILED))
            .isEqualTo("Send OrderPlaced");
        assertThat(EventBusOperationClassifier.getDiagramLabel(send, TrackingVerbosity.SUMMARISED))
            .isEqualTo("→ OrderPlaced");

        EventBusOperationInfo consume = EventBusOperationClassifier.classifyConsume(
            "OrderPlaced", URI.create("rabbitmq://localhost/orders-queue"), null, "m1", "c1");
        assertThat(EventBusOperationClassifier.getDiagramLabel(consume, TrackingVerbosity.SUMMARISED))
            .isEqualTo("← OrderPlaced");
    }

    @Test
    void buildsUriFromDestinationOrMessageType() {
        EventBusOperationInfo send = EventBusOperationClassifier.classifyPublish(
            "OrderPlaced", URI.create("rabbitmq://localhost/orders-queue"), null, null, null);
        assertThat(EventBusOperationClassifier.buildUri(send, TrackingVerbosity.DETAILED).toString())
            .isEqualTo("masstransit:///orders-queue"); // queue name extracted
        assertThat(EventBusOperationClassifier.buildUri(send, TrackingVerbosity.RAW).toString())
            .isEqualTo("rabbitmq://localhost/orders-queue"); // raw destination
        assertThat(EventBusOperationClassifier.buildUri(send, TrackingVerbosity.SUMMARISED).toString())
            .isEqualTo("masstransit:///OrderPlaced");
    }

    // --- recorder ---

    @Test
    void publishEmitsEventStyledPairWithBody() {
        EventBusInteractionRecorder rec = new EventBusInteractionRecorder(opts().build());
        EventBusOperationInfo op = EventBusOperationClassifier.classifyPublish(
            "OrderPlaced", URI.create("rabbitmq://localhost/orders-queue"), null, null, null);
        rec.logPublish(op, java.util.Map.of("id", 1));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.metaType()).isEqualTo(RequestResponseMetaType.EVENT);
        assertThat(req.method().value()).isEqualTo("Publish OrderPlaced");
        assertThat(req.serviceName()).isEqualTo("OrderBus");
        assertThat(req.callerName()).isEqualTo("Test");
        assertThat(req.content()).isEqualTo("{\"id\":1}");        // serialized body
        assertThat(req.uri().toString()).isEqualTo("masstransit:///orders-queue");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);

        RequestResponseLog res = logs.get(1);
        assertThat(res.metaType()).isEqualTo(RequestResponseMetaType.EVENT);
        assertThat(res.content()).isNull();
        assertThat(res.statusCode()).isEqualTo(StatusCode.of("OK"));
        assertThat(res.traceId()).isEqualTo(req.traceId());
    }

    @Test
    void consumeSwapsParticipantsForIncomingDirection() {
        EventBusInteractionRecorder rec = new EventBusInteractionRecorder(opts().build());
        EventBusOperationInfo op = EventBusOperationClassifier.classifyConsume(
            "OrderPlaced", URI.create("rabbitmq://localhost/orders-queue"), null, null, null);
        rec.logConsume(op, java.util.Map.of("id", 1));

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        // swapped: the bus is the caller, the test (service) receives
        assertThat(req.serviceName()).isEqualTo("Test");
        assertThat(req.callerName()).isEqualTo("OrderBus");
    }

    @Test
    void faultEmitsFaultStatusWithExceptionMessage() {
        EventBusInteractionRecorder rec = new EventBusInteractionRecorder(opts().build());
        EventBusOperationInfo op = EventBusOperationClassifier.classifyConsume(
            "OrderPlaced", URI.create("rabbitmq://localhost/orders-queue"), null, null, null);
        rec.logConsumeFault(op, new IllegalStateException("boom"));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).content()).isEqualTo("boom");
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of("Fault"));
    }

    @Test
    void perOperationTogglesAndIdentityGate() {
        EventBusInteractionRecorder noPublish = new EventBusInteractionRecorder(opts().trackPublish(false).build());
        noPublish.logPublish(EventBusOperationClassifier.classifyPublish("X", null, null, null, null), null);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();

        EventBusInteractionRecorder noId = new EventBusInteractionRecorder(
            EventBusTrackerOptions.builder().ids(IdGenerator.seeded(1)).build());
        noId.logSend(EventBusOperationClassifier.classifySend("X", null, null, null, null), null);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
