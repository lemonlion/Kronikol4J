package io.kronikol.azure;

import io.kronikol.core.tracking.TrackingVerbosity;

/**
 * Classifies Azure Service Bus operations from the SDK method name (e.g. {@code SendMessageAsync}), the
 * entity path (queue/topic), and the batch message count. Java port of the .NET
 * {@code ServiceBusOperationClassifier}. Pure logic — the caller (a sender/receiver wrapper) supplies the
 * method name and message count, so no Azure SDK dependency is needed.
 */
public final class ServiceBusOperationClassifier {

    private ServiceBusOperationClassifier() {
    }

    /** Classifies a Service Bus call. {@code messageCount} is the batch size (or null for single-message ops). */
    public static ServiceBusOperationInfo classify(String methodName, String entityPath, Integer messageCount) {
        String queueOrTopic = entityPath == null || entityPath.isEmpty() ? null : entityPath;
        ServiceBusOperation operation = switch (methodName == null ? "" : methodName) {
            case "SendMessageAsync" -> ServiceBusOperation.SEND;
            case "SendMessagesAsync" -> ServiceBusOperation.SEND_BATCH;
            case "ScheduleMessageAsync", "ScheduleMessagesAsync" -> ServiceBusOperation.SCHEDULE;
            case "CancelScheduledMessageAsync", "CancelScheduledMessagesAsync" -> ServiceBusOperation.CANCEL_SCHEDULE;
            case "ReceiveMessageAsync" -> ServiceBusOperation.RECEIVE;
            case "ReceiveMessagesAsync" -> ServiceBusOperation.RECEIVE_BATCH;
            case "PeekMessageAsync", "PeekMessagesAsync" -> ServiceBusOperation.PEEK;
            case "CompleteMessageAsync" -> ServiceBusOperation.COMPLETE;
            case "AbandonMessageAsync" -> ServiceBusOperation.ABANDON;
            case "DeadLetterMessageAsync" -> ServiceBusOperation.DEAD_LETTER;
            case "DeferMessageAsync" -> ServiceBusOperation.DEFER;
            case "RenewMessageLockAsync" -> ServiceBusOperation.RENEW_MESSAGE_LOCK;
            case "RenewSessionLockAsync" -> ServiceBusOperation.RENEW_SESSION_LOCK;
            case "GetSessionStateAsync" -> ServiceBusOperation.GET_SESSION_STATE;
            case "SetSessionStateAsync" -> ServiceBusOperation.SET_SESSION_STATE;
            case "StartProcessingAsync" -> ServiceBusOperation.START_PROCESSING;
            case "StopProcessingAsync" -> ServiceBusOperation.STOP_PROCESSING;
            default -> ServiceBusOperation.OTHER;
        };
        return new ServiceBusOperationInfo(operation, queueOrTopic, messageCount);
    }

    /** The diagram label: Raw uses the operation name; Summarised collapses batches; Detailed adds arrows + counts. */
    public static String getDiagramLabel(ServiceBusOperationInfo op, TrackingVerbosity verbosity) {
        if (verbosity == TrackingVerbosity.RAW) {
            return op.operation().displayName();
        }
        if (verbosity == TrackingVerbosity.SUMMARISED) {
            return switch (op.operation()) {
                case SEND, SEND_BATCH -> "Send";
                case RECEIVE, RECEIVE_BATCH -> "Receive";
                case RENEW_MESSAGE_LOCK -> "RenewLock";
                default -> op.operation().displayName();
            };
        }
        // Detailed
        String queue = op.queueOrTopicName();
        Integer count = op.messageCount();
        return switch (op.operation()) {
            case SEND -> queue != null ? "Send → " + queue : "Send";
            case SEND_BATCH -> queue != null
                ? (count != null ? "Send (×" + count + ") → " + queue : "Send (batch) → " + queue)
                : (count != null ? "Send (×" + count + ")" : "Send (batch)");
            case SCHEDULE -> queue != null ? "Schedule → " + queue : "Schedule";
            case RECEIVE -> queue != null ? "Receive ← " + queue : "Receive";
            case RECEIVE_BATCH -> queue != null
                ? (count != null ? "Receive (×" + count + ") ← " + queue : "Receive (batch) ← " + queue)
                : (count != null ? "Receive (×" + count + ")" : "Receive (batch)");
            case PEEK -> queue != null ? "Peek ← " + queue : "Peek";
            case RENEW_MESSAGE_LOCK -> "RenewLock";
            default -> op.operation().displayName();
        };
    }
}
