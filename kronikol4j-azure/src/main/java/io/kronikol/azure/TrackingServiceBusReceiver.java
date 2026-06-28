package io.kronikol.azure;

import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;

/**
 * Wraps a Service Bus {@link ServiceBusReceiverClient} so each receive/peek is auto-captured as a tracked
 * interaction via {@link ServiceBusInteractionRecorder} — the Java analog of the .NET
 * {@code TrackingServiceBusReceiver}. An explicit decorator (the Java client is a concrete class);
 * {@link #inner()} exposes the underlying client for untracked operations. The Azure SDK is {@code compileOnly}.
 */
public final class TrackingServiceBusReceiver {

    private final ServiceBusReceiverClient inner;
    private final ServiceBusInteractionRecorder recorder;

    public TrackingServiceBusReceiver(ServiceBusReceiverClient inner, ServiceBusTrackerOptions options) {
        this.inner = inner;
        this.recorder = new ServiceBusInteractionRecorder(options);
    }

    /** The underlying real client (for operations this wrapper does not track). */
    public ServiceBusReceiverClient inner() {
        return inner;
    }

    /** Receives up to {@code maxMessages}, tracking it as a {@code ReceiveBatch} with the requested count. */
    public IterableStream<ServiceBusReceivedMessage> receiveMessages(int maxMessages) {
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify(
            "ReceiveMessagesAsync", inner.getEntityPath(), maxMessages);
        try {
            IterableStream<ServiceBusReceivedMessage> result = inner.receiveMessages(maxMessages);
            recorder.record(op, null, null);
            return result;
        } catch (RuntimeException e) {
            recorder.record(op, null, e.getMessage());
            throw e;
        }
    }

    /** Peeks the next message, tracking it as a {@code Peek}. */
    public ServiceBusReceivedMessage peekMessage() {
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify(
            "PeekMessageAsync", inner.getEntityPath(), null);
        try {
            ServiceBusReceivedMessage result = inner.peekMessage();
            recorder.record(op, null, null);
            return result;
        } catch (RuntimeException e) {
            recorder.record(op, null, e.getMessage());
            throw e;
        }
    }
}
