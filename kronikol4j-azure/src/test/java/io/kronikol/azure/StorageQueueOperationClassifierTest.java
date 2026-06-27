package io.kronikol.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies the {@link StorageQueueOperationClassifier} matches the .NET classifier across the operation
 *  matrix (method + path + query) and the diagram labels. */
class StorageQueueOperationClassifierTest {

    private static final String BASE = "https://acct.queue.core.windows.net";

    private static StorageQueueOperationInfo classify(String method, String pathAndQuery) {
        return StorageQueueOperationClassifier.classify(method, URI.create(BASE + pathAndQuery));
    }

    @Test
    void classifiesMessageOperations() {
        assertThat(classify("POST", "/orders/messages").operation())
            .isEqualTo(StorageQueueOperation.SEND_MESSAGE);
        assertThat(classify("GET", "/orders/messages").operation())
            .isEqualTo(StorageQueueOperation.RECEIVE_MESSAGES);
        assertThat(classify("GET", "/orders/messages?peekonly=true").operation())
            .isEqualTo(StorageQueueOperation.PEEK_MESSAGES);
        assertThat(classify("DELETE", "/orders/messages").operation())
            .isEqualTo(StorageQueueOperation.CLEAR_MESSAGES);

        StorageQueueOperationInfo del = classify("DELETE", "/orders/messages/abc123?popreceipt=x");
        assertThat(del.operation()).isEqualTo(StorageQueueOperation.DELETE_MESSAGE);
        assertThat(del.queueName()).isEqualTo("orders");
        assertThat(del.messageId()).isEqualTo("abc123");

        assertThat(classify("PUT", "/orders/messages/abc123?popreceipt=x").operation())
            .isEqualTo(StorageQueueOperation.UPDATE_MESSAGE);
    }

    @Test
    void classifiesQueueAndAccountOperations() {
        assertThat(classify("PUT", "/orders").operation()).isEqualTo(StorageQueueOperation.CREATE_QUEUE);
        assertThat(classify("DELETE", "/orders").operation()).isEqualTo(StorageQueueOperation.DELETE_QUEUE);
        assertThat(classify("GET", "/orders?comp=metadata").operation())
            .isEqualTo(StorageQueueOperation.GET_PROPERTIES);
        assertThat(classify("PUT", "/orders?comp=metadata").operation())
            .isEqualTo(StorageQueueOperation.SET_METADATA);
        assertThat(classify("GET", "/?comp=list").operation()).isEqualTo(StorageQueueOperation.LIST_QUEUES);
    }

    @Test
    void unmatchedPathsAreOther() {
        assertThat(classify("PATCH", "/orders/messages").operation())
            .isEqualTo(StorageQueueOperation.OTHER);
        assertThat(classify("GET", "/a/b/c/d").operation()).isEqualTo(StorageQueueOperation.OTHER);
    }

    @Test
    void diagramLabels() {
        StorageQueueOperationInfo send = classify("POST", "/orders/messages");
        assertThat(StorageQueueOperationClassifier.getDiagramLabel(send, TrackingVerbosity.DETAILED))
            .isEqualTo("Send → orders");
        assertThat(StorageQueueOperationClassifier.getDiagramLabel(send, TrackingVerbosity.SUMMARISED))
            .isEqualTo("SendMessage");

        StorageQueueOperationInfo recv = classify("GET", "/orders/messages");
        assertThat(StorageQueueOperationClassifier.getDiagramLabel(recv, TrackingVerbosity.DETAILED))
            .isEqualTo("Receive ← orders");

        StorageQueueOperationInfo del = classify("DELETE", "/orders/messages/abc123");
        assertThat(StorageQueueOperationClassifier.getDiagramLabel(del, TrackingVerbosity.DETAILED))
            .isEqualTo("Delete");
    }
}
