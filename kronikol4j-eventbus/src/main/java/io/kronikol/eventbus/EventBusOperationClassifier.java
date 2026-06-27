package io.kronikol.eventbus;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;

/**
 * Classifies in-process message-bus operations and labels them for the diagram. Java port of the .NET
 * {@code MassTransitOperationClassifier} — its runtime-agnostic logic (label / URI / queue-name extraction),
 * with parameter-based factory methods replacing the MassTransit {@code Send/Publish/ConsumeContext<T>} the
 * .NET version reads (Java binds these from a Spring {@code ApplicationEvent} / Axon message instead). Pure
 * logic — no messaging-framework dependency.
 */
public final class EventBusOperationClassifier {

    private EventBusOperationClassifier() {
    }

    public static EventBusOperationInfo classifySend(String messageType, URI destination, URI source,
                                                     String messageId, String conversationId) {
        return new EventBusOperationInfo(EventBusOperation.SEND, messageType, destination, source,
            messageId, conversationId);
    }

    public static EventBusOperationInfo classifyPublish(String messageType, URI destination, URI source,
                                                        String messageId, String conversationId) {
        return new EventBusOperationInfo(EventBusOperation.PUBLISH, messageType, destination, source,
            messageId, conversationId);
    }

    public static EventBusOperationInfo classifyConsume(String messageType, URI inputAddress, URI source,
                                                        String messageId, String conversationId) {
        return new EventBusOperationInfo(EventBusOperation.CONSUME, messageType, inputAddress, source,
            messageId, conversationId);
    }

    /** The diagram label for an operation at the given verbosity (matches the .NET {@code GetDiagramLabel}). */
    public static String getDiagramLabel(EventBusOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName() + " " + op.messageType() + " → " + op.destinationAddress();
            case DETAILED -> switch (op.operation()) {
                case SEND -> "Send " + op.messageType();
                case PUBLISH -> "Publish " + op.messageType();
                case CONSUME -> "Consume " + op.messageType();
                case SEND_FAULT -> "Send Fault " + op.messageType();
                case PUBLISH_FAULT -> "Publish Fault " + op.messageType();
                case CONSUME_FAULT -> "Consume Fault " + op.messageType();
                default -> op.operation().displayName();
            };
            case SUMMARISED -> switch (op.operation()) {
                case SEND, PUBLISH -> "→ " + op.messageType();
                case CONSUME -> "← " + op.messageType();
                default -> op.operation().displayName();
            };
        };
    }

    /** The diagram URI for an operation at the given verbosity (matches the .NET {@code BuildUri}). */
    public static URI buildUri(EventBusOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.destinationAddress() != null
                ? op.destinationAddress() : URI.create("masstransit:///unknown");
            case DETAILED -> op.destinationAddress() != null
                ? URI.create("masstransit:///" + extractQueueName(op.destinationAddress()))
                : URI.create("masstransit:///unknown");
            case SUMMARISED -> op.messageType() != null
                ? URI.create("masstransit:///" + op.messageType())
                : URI.create("masstransit:///unknown");
        };
    }

    /** The queue/endpoint name from a bus URI ({@code rabbitmq://host/orders-queue} → {@code orders-queue}). */
    static String extractQueueName(URI uri) {
        String path = uri.getPath();
        if (path != null && !path.isEmpty()) {
            String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            int lastSlash = trimmed.lastIndexOf('/');
            String last = lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
            if (!last.isEmpty()) {
                return last;
            }
        }
        return uri.getHost() != null ? uri.getHost() : "unknown";
    }
}
