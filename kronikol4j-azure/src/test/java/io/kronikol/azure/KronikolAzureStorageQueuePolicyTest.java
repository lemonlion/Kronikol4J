package io.kronikol.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import io.kronikol.azure.AzureTracking.AzureTrackingOptions;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.junit5.KronikolExtension;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Drives the {@link KronikolAzureStorageQueuePolicy}'s recording core ({@code track}) with a real Azure SDK
 * {@link HttpRequest} (no live pipeline) to verify it extracts the method/URL/body and emits the classified
 * Storage Queues pair — the .NET {@code StorageQueueTrackingMessageHandler} behaviour.
 */
@ExtendWith(KronikolExtension.class)
class KronikolAzureStorageQueuePolicyTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void sendMessageIsExtractedClassifiedAndRecorded() {
        var policy = new KronikolAzureStorageQueuePolicy(AzureTrackingOptions.forService("Queues"));
        HttpRequest request = new HttpRequest(
            HttpMethod.POST, "https://acct.queue.core.windows.net/orders/messages");

        policy.track(request, "<QueueMessage><MessageText>hi</MessageText></QueueMessage>", 201);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("Send → orders"); // classifier Detailed label
        assertThat(logs.get(0).uri().toString()).isEqualTo("storagequeue:///orders");
        assertThat(logs.get(0).content()).contains("MessageText");
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of(201));
    }

    @Test
    void receiveMessagesIsClassified() {
        var policy = new KronikolAzureStorageQueuePolicy(AzureTrackingOptions.forService("Queues"));
        HttpRequest request = new HttpRequest(
            HttpMethod.GET, "https://acct.queue.core.windows.net/orders/messages?numofmessages=5");

        policy.track(request, null, 200);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("Receive ← orders"); // GET /messages → receive
        assertThat(req.uri().toString()).isEqualTo("storagequeue:///orders");
    }
}
