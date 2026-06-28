package io.kronikol.azure;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Service Bus {@link ServiceBusSenderClient} so each send is auto-captured as a tracked interaction
 * via {@link ServiceBusInteractionRecorder} — the Java analog of the .NET {@code TrackingServiceBusSender}.
 * Service Bus is AMQP (no HTTP pipeline), and the Java {@code ServiceBusSenderClient} is a concrete class, so
 * this is an explicit decorator (call its methods instead of the raw client's); {@link #inner()} exposes the
 * underlying client for untracked operations. The Azure SDK is {@code compileOnly}.
 */
public final class TrackingServiceBusSender {

    private final ServiceBusSenderClient inner;
    private final ServiceBusInteractionRecorder recorder;

    public TrackingServiceBusSender(ServiceBusSenderClient inner, ServiceBusTrackerOptions options) {
        this.inner = inner;
        this.recorder = new ServiceBusInteractionRecorder(options);
    }

    /** The underlying real client (for operations this wrapper does not track). */
    public ServiceBusSenderClient inner() {
        return inner;
    }

    /** Sends one message, tracking it as a {@code Send} (records the failure message on error). */
    public void sendMessage(ServiceBusMessage message) {
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify(
            "SendMessageAsync", inner.getEntityPath(), null);
        track(op, bodyOf(message), () -> inner.sendMessage(message));
    }

    /** Sends a batch of messages, tracking it as a {@code SendBatch} with the message count. */
    public void sendMessages(Iterable<ServiceBusMessage> messages) {
        List<ServiceBusMessage> list = new ArrayList<>();
        messages.forEach(list::add);
        ServiceBusOperationInfo op = ServiceBusOperationClassifier.classify(
            "SendMessagesAsync", inner.getEntityPath(), list.size());
        String content = list.isEmpty() ? null : bodyOf(list.get(0));
        track(op, content, () -> inner.sendMessages(list));
    }

    private void track(ServiceBusOperationInfo op, String content, Runnable send) {
        try {
            send.run();
            recorder.record(op, content, null);
        } catch (RuntimeException e) {
            recorder.record(op, content, e.getMessage());
            throw e;
        }
    }

    private static String bodyOf(ServiceBusMessage message) {
        try {
            return message.getBody() == null ? null : message.getBody().toString();
        } catch (Exception e) {
            return null;
        }
    }
}
