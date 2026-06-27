package io.kronikol.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies Cloud Storage classification (path + method + query/upload) matches the .NET classifier. */
class CloudStorageOperationClassifierTest {

    private static final String API = "https://storage.googleapis.com";

    private static CloudStorageOperationInfo classify(String method, String url) {
        return CloudStorageOperationClassifier.classify(method, URI.create(API + url));
    }

    @Test
    void objectOperations() {
        CloudStorageOperationInfo put = classify("PUT", "/storage/v1/b/mybucket/o/file.txt");
        assertThat(put.operation()).isEqualTo(CloudStorageOperation.UPLOAD);
        assertThat(put.bucketName()).isEqualTo("mybucket");
        assertThat(put.objectName()).isEqualTo("file.txt");

        assertThat(classify("GET", "/storage/v1/b/mybucket/o/file.txt?alt=media").operation())
            .isEqualTo(CloudStorageOperation.DOWNLOAD);
        assertThat(classify("GET", "/storage/v1/b/mybucket/o/file.txt").operation())
            .isEqualTo(CloudStorageOperation.GET_METADATA);
        assertThat(classify("DELETE", "/storage/v1/b/mybucket/o/file.txt").operation())
            .isEqualTo(CloudStorageOperation.DELETE);
        assertThat(classify("PATCH", "/storage/v1/b/mybucket/o/file.txt").operation())
            .isEqualTo(CloudStorageOperation.UPDATE_METADATA);
        assertThat(classify("GET", "/storage/v1/b/mybucket/o").operation())
            .isEqualTo(CloudStorageOperation.LIST_OBJECTS);
    }

    @Test
    void uploadViaUploadPrefix() {
        assertThat(CloudStorageOperationClassifier.classify("POST",
            URI.create(API + "/upload/storage/v1/b/mybucket/o")).operation())
            .isEqualTo(CloudStorageOperation.UPLOAD);
    }

    @Test
    void copyAndCompose() {
        assertThat(classify("POST", "/storage/v1/b/src/o/file/copyTo/b/dst/o/file2").operation())
            .isEqualTo(CloudStorageOperation.COPY);
        assertThat(classify("POST", "/storage/v1/b/mybucket/o/file/compose").operation())
            .isEqualTo(CloudStorageOperation.COMPOSE);
    }

    @Test
    void bucketOperations() {
        assertThat(classify("GET", "/storage/v1/b/mybucket").operation())
            .isEqualTo(CloudStorageOperation.GET_BUCKET);
        assertThat(classify("DELETE", "/storage/v1/b/mybucket").operation())
            .isEqualTo(CloudStorageOperation.DELETE_BUCKET);
        assertThat(classify("POST", "/storage/v1/b").operation()).isEqualTo(CloudStorageOperation.CREATE_BUCKET);
        assertThat(classify("GET", "/storage/v1/b").operation()).isEqualTo(CloudStorageOperation.LIST_BUCKETS);
    }

    @Test
    void percentEncodedObjectName() {
        CloudStorageOperationInfo info = classify("GET", "/storage/v1/b/mybucket/o/folder%2Ffile.txt");
        assertThat(info.objectName()).isEqualTo("folder/file.txt");
    }

    @Test
    void diagramLabel() {
        CloudStorageOperationInfo info = classify("PUT", "/storage/v1/b/mybucket/o/file.txt");
        assertThat(CloudStorageOperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED))
            .isEqualTo("Upload → mybucket/file.txt");
        assertThat(CloudStorageOperationClassifier.getDiagramLabel(info, TrackingVerbosity.SUMMARISED))
            .isEqualTo("Upload");
    }
}
