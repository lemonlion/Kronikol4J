package io.kronikol.eventbus;

import java.net.URI;

/**
 * The result of classifying a message-bus operation. Java port of the .NET {@code MassTransitOperationInfo}
 * (message ids kept as opaque strings — a generic bus need not use GUIDs).
 *
 * @param operation          the classified operation
 * @param messageType        the message/event type name (or {@code null})
 * @param destinationAddress the destination endpoint (or {@code null})
 * @param sourceAddress      the source endpoint (or {@code null})
 * @param messageId          the message id (or {@code null})
 * @param conversationId     the conversation/correlation id (or {@code null})
 */
public record EventBusOperationInfo(EventBusOperation operation, String messageType, URI destinationAddress,
                                    URI sourceAddress, String messageId, String conversationId) {

    public EventBusOperationInfo(EventBusOperation operation, String messageType, URI destinationAddress) {
        this(operation, messageType, destinationAddress, null, null, null);
    }
}
