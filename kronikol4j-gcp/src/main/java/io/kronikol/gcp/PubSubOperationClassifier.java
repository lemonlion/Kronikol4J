package io.kronikol.gcp;

import io.kronikol.core.tracking.TrackingVerbosity;

/**
 * Classifies Google Cloud Pub/Sub operations from the SDK method name (e.g. {@code PublishAsync}), the topic
 * + subscription names, and the batch message count. Java port of the .NET {@code PubSubOperationClassifier}.
 * Pure logic — the caller (a publisher/subscriber wrapper) supplies the method name + count, so no Pub/Sub
 * SDK dependency is needed.
 */
public final class PubSubOperationClassifier {

    private PubSubOperationClassifier() {
    }

    /** Classifies a Pub/Sub call; a {@code PublishAsync} with {@code messageCount > 1} is a batch publish. */
    public static PubSubOperationInfo classify(String methodName, String topicName, String subscriptionName,
                                               Integer messageCount) {
        PubSubOperation operation = switch (methodName == null ? "" : methodName) {
            case "PublishAsync" ->
                messageCount != null && messageCount > 1 ? PubSubOperation.PUBLISH_BATCH : PubSubOperation.PUBLISH;
            case "PullAsync" -> PubSubOperation.PULL;
            case "AcknowledgeAsync" -> PubSubOperation.ACKNOWLEDGE;
            case "ModifyAckDeadlineAsync" -> PubSubOperation.MODIFY_ACK_DEADLINE;
            case "Receive" -> PubSubOperation.RECEIVE;
            case "StartAsync" -> PubSubOperation.START_SUBSCRIBER;
            case "StopAsync" -> PubSubOperation.STOP_SUBSCRIBER;
            default -> PubSubOperation.OTHER;
        };
        return new PubSubOperationInfo(operation, topicName, subscriptionName, messageCount);
    }

    /** The diagram label per verbosity (Detailed uses directional arrows + the short resource name). */
    public static String getDiagramLabel(PubSubOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName()
                + " topic=" + orEmpty(op.topicName()) + " sub=" + orEmpty(op.subscriptionName());
            case DETAILED -> switch (op.operation()) {
                case PUBLISH -> "Publish → " + shortName(op.topicName());
                case PUBLISH_BATCH -> "Publish (×" + op.messageCount() + ") → " + shortName(op.topicName());
                case PULL -> "Pull ← " + shortName(op.subscriptionName());
                case RECEIVE -> "Receive ← " + shortName(op.subscriptionName());
                case ACKNOWLEDGE -> "Ack";
                default -> op.operation().displayName();
            };
            case SUMMARISED -> op.operation() == PubSubOperation.PUBLISH_BATCH
                ? "Publish" : op.operation().displayName();
        };
    }

    /** The last path segment of a fully-qualified Pub/Sub resource name (e.g. {@code projects/p/topics/orders}). */
    private static String shortName(String fullName) {
        if (fullName == null) {
            return null;
        }
        int slash = fullName.lastIndexOf('/');
        return slash >= 0 ? fullName.substring(slash + 1) : fullName;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
