package io.kronikol.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import io.kronikol.azure.AzureTracking.AzureTrackingOptions;
import io.kronikol.core.constants.DependencyCategories;
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
 * Drives the unified {@link KronikolAzureTrackingPolicy}'s recording core ({@code track}) with real Azure SDK
 * {@link HttpRequest}s (no live pipeline) to verify the per-service routing (Cosmos / Blob / Storage Queues),
 * clean URIs, categories, and the Cosmos query/upsert header flags.
 */
@ExtendWith(KronikolExtension.class)
class KronikolAzureTrackingPolicyTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    private static KronikolAzureTrackingPolicy policy() {
        return new KronikolAzureTrackingPolicy(AzureTrackingOptions.forService("Azure"));
    }

    @Test
    void cosmosReadDocumentIsClassifiedWithCleanPathUri() {
        HttpRequest request = new HttpRequest(HttpMethod.GET,
            "https://acct.documents.azure.com/dbs/shop/colls/orders/docs/o-1");

        policy().track(request, null, 200);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.method().value()).isEqualTo("Read"); // GET a document → Read
        assertThat(req.uri().toString()).isEqualTo("https://acct.documents.azure.com/colls/orders/docs/o-1");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.COSMOS_DB);
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of(200));
    }

    @Test
    void cosmosQueryHeaderDrivesQueryClassification() {
        HttpRequest request = new HttpRequest(HttpMethod.POST,
            "https://acct.documents.azure.com/dbs/shop/colls/orders/docs")
            .setHeader(HttpHeaderName.fromString("x-ms-documentdb-isquery"), "True");

        policy().track(request, "{\"query\":\"SELECT * FROM c\"}", 200);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("Query"); // isquery header → Query
        assertThat(req.uri().toString()).isEqualTo("https://acct.documents.azure.com/colls/orders");
    }

    @Test
    void blobPutIsClassifiedWithContainerBlobUri() {
        HttpRequest request = new HttpRequest(HttpMethod.PUT,
            "https://acct.blob.core.windows.net/files/photo.jpg?sig=secret");

        policy().track(request, null, 201);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.uri().toString()).isEqualTo("https://acct.blob.core.windows.net/files/photo.jpg"); // query stripped
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.BLOB_STORAGE);
    }

    @Test
    void storageQueueSendIsRoutedToTheQueueRecorder() {
        HttpRequest request = new HttpRequest(HttpMethod.POST,
            "https://acct.queue.core.windows.net/orders/messages");

        policy().track(request, "<msg/>", 201);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("Send → orders");
        assertThat(req.uri().toString()).isEqualTo("storagequeue:///orders");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);
    }

    @Test
    void unrecognisedHostIsNotTracked() {
        policy().track(new HttpRequest(HttpMethod.GET, "https://example.com/api"), null, 200);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
