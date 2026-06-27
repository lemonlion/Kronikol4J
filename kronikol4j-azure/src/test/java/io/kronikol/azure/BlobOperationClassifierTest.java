package io.kronikol.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies Blob Storage classification (path + query params + method) matches the .NET classifier. */
class BlobOperationClassifierTest {

    private static final String BASE = "https://acct.blob.core.windows.net";

    private static BlobOperation op(String method, String pathAndQuery) {
        return BlobOperationClassifier.classify(method, URI.create(BASE + pathAndQuery)).operation();
    }

    @Test
    void blobCrud() {
        BlobOperationInfo upload = BlobOperationClassifier.classify("PUT", URI.create(BASE + "/cont/blob.txt"));
        assertThat(upload.operation()).isEqualTo(BlobOperation.UPLOAD);
        assertThat(upload.containerName()).isEqualTo("cont");
        assertThat(upload.blobName()).isEqualTo("blob.txt");

        assertThat(op("GET", "/cont/blob.txt")).isEqualTo(BlobOperation.DOWNLOAD);
        assertThat(op("DELETE", "/cont/blob.txt")).isEqualTo(BlobOperation.DELETE);
        assertThat(op("HEAD", "/cont/blob.txt")).isEqualTo(BlobOperation.GET_PROPERTIES);
    }

    @Test
    void containerOperations() {
        assertThat(op("PUT", "/cont?restype=container")).isEqualTo(BlobOperation.CREATE_CONTAINER);
        assertThat(op("DELETE", "/cont?restype=container")).isEqualTo(BlobOperation.DELETE_CONTAINER);
        assertThat(op("GET", "/cont?restype=container&comp=list")).isEqualTo(BlobOperation.LIST_BLOBS);
    }

    @Test
    void metadataAndComposition() {
        assertThat(op("PUT", "/cont/blob?comp=metadata")).isEqualTo(BlobOperation.SET_METADATA);
        assertThat(op("GET", "/cont/blob?comp=metadata")).isEqualTo(BlobOperation.GET_METADATA);
        assertThat(op("PUT", "/cont/blob?comp=copy")).isEqualTo(BlobOperation.COPY);
        assertThat(op("PUT", "/cont/blob?comp=block&blockid=abc")).isEqualTo(BlobOperation.PUT_BLOCK);
        assertThat(op("PUT", "/cont/blob?comp=blocklist")).isEqualTo(BlobOperation.PUT_BLOCK_LIST);
        assertThat(op("PUT", "/cont/blob?comp=lease")).isEqualTo(BlobOperation.LEASE);
    }

    @Test
    void diagramLabel() {
        BlobOperationInfo info = BlobOperationClassifier.classify("PUT", URI.create(BASE + "/cont/blob.txt"));
        assertThat(BlobOperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED)).isEqualTo("Upload");
        assertThat(BlobOperationClassifier.getDiagramLabel(info, TrackingVerbosity.RAW)).isNull();
    }
}
