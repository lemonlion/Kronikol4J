package io.kronikol.azure;

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
 * Verifies the Service Bus recorder matches the .NET {@code ServiceBusTracker}: event-styled pair on the
 * {@code ServiceBus} category, {@code servicebus://<queue>[/<sub>]} URI, no HTTP status, send/receive/peek
 * event-styling, and Summarised body-drop.
 */
class ServiceBusInteractionRecorderTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static ServiceBusInteractionRecorder recorder(TrackingVerbosity verbosity) {
        return new ServiceBusInteractionRecorder(ServiceBusTrackerOptions.builder()
            .serviceName("Bus").callerName("Test")
            .verbosity(verbosity)
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build());
    }

    @Test
    void sendEmitsEventStyledPairOnServiceBusCategory() {
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify("SendMessageAsync", "orders", null);
        recorder(TrackingVerbosity.DETAILED).record(op, "{\"id\":1}", null);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("Send → orders");
        assertThat(req.uri().toString()).isEqualTo("servicebus://orders");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.SERVICE_BUS);
        assertThat(req.metaType()).isEqualTo(RequestResponseMetaType.EVENT);
        assertThat(req.content()).isEqualTo("{\"id\":1}");
        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.statusCode()).isNull(); // events carry no HTTP status (matches .NET)
        assertThat(req.traceId()).isEqualTo(res.traceId());
    }

    @Test
    void errorIsRecordedAsTheResponseContent() {
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify("SendMessageAsync", "orders", null);
        recorder(TrackingVerbosity.DETAILED).record(op, "{\"id\":1}", "connection reset");

        assertThat(RequestResponseLogger.getAllLogs().get(1).content()).isEqualTo("connection reset");
    }

    @Test
    void completeIsNotEventStyled() {
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify("CompleteMessageAsync", "orders", null);
        recorder(TrackingVerbosity.DETAILED).record(op, null, null);

        assertThat(RequestResponseLogger.getAllLogs().get(0).metaType())
            .isEqualTo(RequestResponseMetaType.DEFAULT); // Complete is not a send/receive/peek/schedule op
    }

    @Test
    void summarisedDropsBodyAndCollapsesUri() {
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify("SendMessageAsync", "orders", null);
        recorder(TrackingVerbosity.SUMMARISED).record(op, "{\"id\":1}", null);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("Send");      // Summarised collapses the label
        assertThat(req.uri().toString()).isEqualTo("servicebus://orders/"); // trailing slash form
        assertThat(req.content()).isNull();                      // body dropped
    }
}
