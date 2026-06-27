package io.kronikol.aws;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Amazon SQS requests into typed operations from the {@code X-Amz-Target} header (JSON protocol),
 * the {@code Action} query/form parameter (query protocol), and the queue URL/name. Java port of the .NET
 * {@code SqsOperationClassifier}. Pure logic on the request signals — no AWS SDK dependency; the
 * {@code ExecutionInterceptor} that feeds it is separate.
 */
public final class SqsOperationClassifier {

    private static final Pattern TARGET = Pattern.compile("AmazonSQS\\.(?<op>\\w+)");
    private static final Pattern ACTION = Pattern.compile("Action=(?<op>\\w+)");
    private static final Pattern QUEUE_URL_PATH = Pattern.compile("/\\d+/(?<queue>[^/?]+)");
    private static final Pattern QUEUE_NAME_BODY = Pattern.compile("\"QueueName\"\\s*:\\s*\"(?<queue>[^\"]+)\"");
    private static final Pattern QUEUE_URL_BODY =
        Pattern.compile("\"QueueUrl\"\\s*:\\s*\"[^\"]*?/\\d+/(?<queue>[^\"]+)\"");

    private SqsOperationClassifier() {
    }

    /**
     * Classifies an SQS request. {@code xAmzTarget} is the {@code X-Amz-Target} header value (or null);
     * {@code uri} and {@code requestBody} provide the fallback signals.
     */
    public static SqsOperationInfo classify(String xAmzTarget, URI uri, String requestBody) {
        String operationName = null;

        // 1. X-Amz-Target header (JSON protocol).
        if (xAmzTarget != null) {
            Matcher m = TARGET.matcher(xAmzTarget);
            if (m.find()) {
                operationName = m.group("op");
            }
        }
        // 2. Action from query string (query protocol).
        if (operationName == null && uri != null && uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            Matcher m = ACTION.matcher(uri.getQuery());
            if (m.find()) {
                operationName = m.group("op");
            }
        }
        // 3. Action from form body (query protocol POST).
        if (operationName == null && requestBody != null) {
            Matcher m = ACTION.matcher(requestBody);
            if (m.find()) {
                operationName = m.group("op");
            }
        }

        SqsOperation operation = operationName != null ? mapOperation(operationName) : SqsOperation.OTHER;
        return new SqsOperationInfo(operation, extractQueueName(uri, requestBody));
    }

    /** The diagram label: the operation name for Detailed/Summarised; null for Raw (caller uses method+target). */
    public static String getDiagramLabel(SqsOperationInfo op, TrackingVerbosity verbosity) {
        return verbosity == TrackingVerbosity.RAW ? null : op.operation().displayName();
    }

    private static SqsOperation mapOperation(String operationName) {
        return switch (operationName) {
            case "SendMessage" -> SqsOperation.SEND_MESSAGE;
            case "SendMessageBatch" -> SqsOperation.SEND_MESSAGE_BATCH;
            case "ReceiveMessage" -> SqsOperation.RECEIVE_MESSAGE;
            case "DeleteMessage" -> SqsOperation.DELETE_MESSAGE;
            case "DeleteMessageBatch" -> SqsOperation.DELETE_MESSAGE_BATCH;
            case "ChangeMessageVisibility" -> SqsOperation.CHANGE_MESSAGE_VISIBILITY;
            case "ChangeMessageVisibilityBatch" -> SqsOperation.CHANGE_MESSAGE_VISIBILITY_BATCH;
            case "CreateQueue" -> SqsOperation.CREATE_QUEUE;
            case "DeleteQueue" -> SqsOperation.DELETE_QUEUE;
            case "GetQueueUrl" -> SqsOperation.GET_QUEUE_URL;
            case "GetQueueAttributes" -> SqsOperation.GET_QUEUE_ATTRIBUTES;
            case "SetQueueAttributes" -> SqsOperation.SET_QUEUE_ATTRIBUTES;
            case "PurgeQueue" -> SqsOperation.PURGE_QUEUE;
            case "ListQueues" -> SqsOperation.LIST_QUEUES;
            default -> SqsOperation.OTHER;
        };
    }

    private static String extractQueueName(URI uri, String requestBody) {
        // 1. URL path: /account-id/queue-name
        if (uri != null && uri.getPath() != null) {
            Matcher m = QUEUE_URL_PATH.matcher(uri.getPath());
            if (m.find()) {
                return m.group("queue");
            }
        }
        if (requestBody == null || requestBody.isEmpty()) {
            return null;
        }
        // 2. QueueUrl from body.
        Matcher url = QUEUE_URL_BODY.matcher(requestBody);
        if (url.find()) {
            return url.group("queue");
        }
        // 3. QueueName from body.
        Matcher name = QUEUE_NAME_BODY.matcher(requestBody);
        return name.find() ? name.group("queue") : null;
    }
}
