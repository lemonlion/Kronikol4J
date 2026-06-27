package io.kronikol.azure;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Azure Storage Queues REST requests into typed operations from the HTTP method, the
 * {@code /{queue}/messages[/{messageId}]} path, and the {@code comp}/{@code peekonly} query parameters.
 * Java port of the .NET {@code StorageQueueOperationClassifier} — the message-handler analog's classification
 * core. Pure logic — no Azure SDK dependency.
 */
public final class StorageQueueOperationClassifier {

    private static final Pattern QUEUE_PATH =
        Pattern.compile("^/(?<queue>[^/?]+)(?:/messages(?:/(?<msgId>[^/?]+))?)?$");

    private StorageQueueOperationClassifier() {
    }

    /** Classifies a Storage Queues request from its HTTP method and URI. */
    public static StorageQueueOperationInfo classify(String httpMethod, URI uri) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();
        String query = uri == null || uri.getQuery() == null ? "" : uri.getQuery();

        // List queues: GET /?comp=list
        if (path.equals("/") && query.contains("comp=list")) {
            return new StorageQueueOperationInfo(StorageQueueOperation.LIST_QUEUES, null);
        }

        Matcher m = QUEUE_PATH.matcher(path);
        if (!m.matches()) {
            return new StorageQueueOperationInfo(StorageQueueOperation.OTHER, null);
        }

        String queue = m.group("queue");
        String msgIdGroup = m.group("msgId");
        String messageId = msgIdGroup != null && !msgIdGroup.isEmpty() ? msgIdGroup : null;
        boolean hasMessages = path.contains("/messages");
        boolean hasMsgId = messageId != null;

        StorageQueueOperation op = switch (method) {
            case "POST" -> hasMessages && !hasMsgId ? StorageQueueOperation.SEND_MESSAGE
                : StorageQueueOperation.OTHER;
            case "GET" -> {
                if (hasMessages && !hasMsgId) {
                    yield query.contains("peekonly=true")
                        ? StorageQueueOperation.PEEK_MESSAGES
                        : StorageQueueOperation.RECEIVE_MESSAGES;
                }
                if (!hasMessages && query.contains("comp=metadata")) {
                    yield StorageQueueOperation.GET_PROPERTIES;
                }
                yield StorageQueueOperation.OTHER;
            }
            case "DELETE" -> {
                if (hasMessages && hasMsgId) {
                    yield StorageQueueOperation.DELETE_MESSAGE;
                }
                if (hasMessages) {
                    yield StorageQueueOperation.CLEAR_MESSAGES;
                }
                yield StorageQueueOperation.DELETE_QUEUE;
            }
            case "PUT" -> {
                if (hasMessages && hasMsgId) {
                    yield StorageQueueOperation.UPDATE_MESSAGE;
                }
                if (!hasMessages && query.contains("comp=metadata")) {
                    yield StorageQueueOperation.SET_METADATA;
                }
                if (!hasMessages) {
                    yield StorageQueueOperation.CREATE_QUEUE;
                }
                yield StorageQueueOperation.OTHER;
            }
            default -> StorageQueueOperation.OTHER;
        };
        return new StorageQueueOperationInfo(op, queue, messageId);
    }

    /** The diagram label for an operation at the given verbosity (matches the .NET {@code GetDiagramLabel}). */
    public static String getDiagramLabel(StorageQueueOperationInfo op, TrackingVerbosity verbosity) {
        if (verbosity == TrackingVerbosity.DETAILED) {
            return switch (op.operation()) {
                case SEND_MESSAGE -> "Send → " + op.queueName();
                case RECEIVE_MESSAGES -> "Receive ← " + op.queueName();
                case PEEK_MESSAGES -> "Peek ← " + op.queueName();
                case DELETE_MESSAGE -> "Delete";
                case UPDATE_MESSAGE -> "Update";
                case CLEAR_MESSAGES -> "Clear → " + op.queueName();
                default -> op.operation().displayName();
            };
        }
        // Summarised and Raw both use the operation's PascalCase name (Raw's arrow uses the HTTP method).
        return op.operation().displayName();
    }
}
