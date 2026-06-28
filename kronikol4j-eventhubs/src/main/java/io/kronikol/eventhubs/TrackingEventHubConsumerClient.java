package io.kronikol.eventhubs;

import com.azure.core.util.IterableStream;
import com.azure.messaging.eventhubs.EventHubConsumerClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.PartitionEvent;
import java.util.Optional;

/**
 * Wraps an Event Hubs {@link EventHubConsumerClient} so each partition read is auto-captured as a tracked
 * interaction via {@link EventHubsInteractionRecorder} — the Java analog of the .NET
 * {@code TrackingEventHubConsumerClient}. An explicit decorator (the Java client is a concrete class);
 * {@link #inner()} exposes the underlying client for untracked operations. The Azure SDK is {@code compileOnly}.
 */
public final class TrackingEventHubConsumerClient {

    private final EventHubConsumerClient inner;
    private final EventHubsInteractionRecorder recorder;

    public TrackingEventHubConsumerClient(EventHubConsumerClient inner, EventHubsTrackerOptions options) {
        this.inner = inner;
        this.recorder = new EventHubsInteractionRecorder(options);
    }

    /** The underlying real client (for operations this wrapper does not track). */
    public EventHubConsumerClient inner() {
        return inner;
    }

    /** Reads up to {@code maxMessages} from {@code partitionId}, tracking it as a partition read. */
    public IterableStream<PartitionEvent> receiveFromPartition(String partitionId, int maxMessages,
                                                               EventPosition startingPosition) {
        EventHubsOperationInfo op = EventHubsOperationClassifier.classify(
            "ReadEventsFromPartitionAsync", inner.getEventHubName(), partitionId, null);
        Optional<EventHubsInteractionRecorder.Correlation> corr = recorder.logRequest(op, null);
        try {
            IterableStream<PartitionEvent> result =
                inner.receiveFromPartition(partitionId, maxMessages, startingPosition);
            corr.ifPresent(c -> recorder.logResponse(op, c, null));
            return result;
        } catch (RuntimeException e) {
            corr.ifPresent(c -> recorder.logResponse(op, c, e.getMessage()));
            throw e;
        }
    }
}
