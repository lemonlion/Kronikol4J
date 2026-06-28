package io.kronikol.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Pub/Sub recorder matches the .NET {@code PubSubTracker}: event-styled request + plain response
 * pair on the {@code MessageQueue} category, {@code pubsub:///<short name>} URI, no HTTP status, and
 * Summarised body-drop.
 */
class PubSubInteractionRecorderTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static PubSubInteractionRecorder recorder(TrackingVerbosity verbosity) {
        return new PubSubInteractionRecorder(PubSubTrackerOptions.builder()
            .serviceName("Bus").callerName("Test")
            .verbosity(verbosity)
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build());
    }

    @Test
    void publishEmitsEventRequestAndPlainResponse() {
        PubSubOperationInfo op = PubSubOperationClassifier.classify(
            "PublishAsync", "projects/p/topics/orders", null, 1);
        recorder(TrackingVerbosity.DETAILED).record(op, "{\"id\":1}", "msg-id-7");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("Publish → orders"); // short name
        assertThat(req.uri().toString()).isEqualTo("pubsub:///orders");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);
        assertThat(req.metaType()).isEqualTo(RequestResponseMetaType.EVENT);
        assertThat(req.content()).isEqualTo("{\"id\":1}");
        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.metaType()).isEqualTo(RequestResponseMetaType.DEFAULT); // response is plain (matches .NET)
        assertThat(res.statusCode()).isNull(); // events carry no HTTP status
        assertThat(res.content()).isEqualTo("msg-id-7");
        assertThat(req.traceId()).isEqualTo(res.traceId());
    }

    @Test
    void receiveUsesSubscriptionShortName() {
        PubSubOperationInfo op = PubSubOperationClassifier.classify(
            "Receive", null, "projects/p/subscriptions/orders-sub", 1);
        recorder(TrackingVerbosity.DETAILED).record(op, "{}", null);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("Receive ← orders-sub");
        assertThat(req.uri().toString()).isEqualTo("pubsub:///orders-sub");
    }

    @Test
    void summarisedDropsBody() {
        PubSubOperationInfo op = PubSubOperationClassifier.classify(
            "PublishAsync", "projects/p/topics/orders", null, 1);
        recorder(TrackingVerbosity.SUMMARISED).record(op, "{\"id\":1}", "msg-id-7");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(0).content()).isNull();
        assertThat(logs.get(1).content()).isNull();
    }
}
