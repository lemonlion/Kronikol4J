package io.kronikol.aws;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Amazon SNS requests into typed operations from the {@code X-Amz-Target} header (JSON protocol)
 * and the {@code Action} query/form parameter (query protocol), extracting the topic name + ARN from the
 * request body. Java port of the .NET {@code SnsOperationClassifier}. Pure logic — no AWS SDK dependency.
 */
public final class SnsOperationClassifier {

    private static final Pattern TARGET = Pattern.compile("AmazonSimpleNotificationService\\.(?<op>\\w+)");
    private static final Pattern ACTION = Pattern.compile("Action=(?<op>\\w+)");
    private static final Pattern TOPIC_ARN_BODY = Pattern.compile(
        "\"(?:TopicArn|TargetArn)\"\\s*:\\s*\"arn:aws:sns:[^:]+:[^:]+:(?<topic>[^\"]+)\"");
    private static final Pattern FULL_ARN_BODY =
        Pattern.compile("\"(?:TopicArn|TargetArn)\"\\s*:\\s*\"(?<arn>[^\"]+)\"");

    private SnsOperationClassifier() {
    }

    /** Classifies an SNS request from the {@code X-Amz-Target} header, the URI, and the request body. */
    public static SnsOperationInfo classify(String xAmzTarget, URI uri, String requestBody) {
        String operationName = null;

        if (xAmzTarget != null) {
            Matcher m = TARGET.matcher(xAmzTarget);
            if (m.find()) {
                operationName = m.group("op");
            }
        }
        if (operationName == null && uri != null && uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            Matcher m = ACTION.matcher(uri.getQuery());
            if (m.find()) {
                operationName = m.group("op");
            }
        }
        if (operationName == null && requestBody != null) {
            Matcher m = ACTION.matcher(requestBody);
            if (m.find()) {
                operationName = m.group("op");
            }
        }

        SnsOperation operation = operationName != null ? mapOperation(operationName) : SnsOperation.OTHER;
        String topicName = null;
        String topicArn = null;
        if (requestBody != null && !requestBody.isEmpty()) {
            Matcher arn = TOPIC_ARN_BODY.matcher(requestBody);
            if (arn.find()) {
                topicName = arn.group("topic");
                Matcher full = FULL_ARN_BODY.matcher(requestBody);
                topicArn = full.find() ? full.group("arn") : null;
            }
        }
        return new SnsOperationInfo(operation, topicName, topicArn);
    }

    /** The diagram label: the operation name for Detailed/Summarised; null for Raw. */
    public static String getDiagramLabel(SnsOperationInfo op, TrackingVerbosity verbosity) {
        return verbosity == TrackingVerbosity.RAW ? null : op.operation().displayName();
    }

    private static SnsOperation mapOperation(String operationName) {
        return switch (operationName) {
            case "Publish" -> SnsOperation.PUBLISH;
            case "PublishBatch" -> SnsOperation.PUBLISH_BATCH;
            case "Subscribe" -> SnsOperation.SUBSCRIBE;
            case "Unsubscribe" -> SnsOperation.UNSUBSCRIBE;
            case "CreateTopic" -> SnsOperation.CREATE_TOPIC;
            case "DeleteTopic" -> SnsOperation.DELETE_TOPIC;
            case "ListTopics" -> SnsOperation.LIST_TOPICS;
            case "ListSubscriptions" -> SnsOperation.LIST_SUBSCRIPTIONS;
            case "ListSubscriptionsByTopic" -> SnsOperation.LIST_SUBSCRIPTIONS_BY_TOPIC;
            case "GetTopicAttributes" -> SnsOperation.GET_TOPIC_ATTRIBUTES;
            case "SetTopicAttributes" -> SnsOperation.SET_TOPIC_ATTRIBUTES;
            case "ConfirmSubscription" -> SnsOperation.CONFIRM_SUBSCRIPTION;
            default -> SnsOperation.OTHER;
        };
    }
}
