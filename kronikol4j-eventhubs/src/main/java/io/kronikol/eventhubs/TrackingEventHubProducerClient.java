package io.kronikol.eventhubs;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wraps an Event Hubs {@link EventHubProducerClient} so each send is auto-captured as a tracked interaction
 * via {@link EventHubsInteractionRecorder} — the Java analog of the .NET {@code TrackingEventHubProducerClient}.
 * Event Hubs is AMQP and the Java client is a concrete class, so this is an explicit decorator;
 * {@link #inner()} exposes the underlying client for untracked operations. The Azure SDK is {@code compileOnly}.
 */
public final class TrackingEventHubProducerClient {

    private final EventHubProducerClient inner;
    private final EventHubsInteractionRecorder recorder;

    public TrackingEventHubProducerClient(EventHubProducerClient inner, EventHubsTrackerOptions options) {
        this.inner = inner;
        this.recorder = new EventHubsInteractionRecorder(options);
    }

    /** The underlying real client (for operations this wrapper does not track). */
    public EventHubProducerClient inner() {
        return inner;
    }

    /** Sends a set of events, tracking it as a {@code Send}/{@code SendBatch} (records the failure on error). */
    public void send(Iterable<EventData> events) {
        List<EventData> list = new ArrayList<>();
        events.forEach(list::add);
        EventHubsOperationInfo op = EventHubsOperationClassifier.classify(
            "SendAsync", inner.getEventHubName(), null, list.size());
        String content = list.isEmpty() ? null : bodyOf(list.get(0));
        Optional<EventHubsInteractionRecorder.Correlation> corr = recorder.logRequest(op, content);
        try {
            inner.send(list);
            corr.ifPresent(c -> recorder.logResponse(op, c, null));
        } catch (RuntimeException e) {
            corr.ifPresent(c -> recorder.logResponse(op, c, e.getMessage()));
            throw e;
        }
    }

    private static String bodyOf(EventData event) {
        try {
            return event.getBodyAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
