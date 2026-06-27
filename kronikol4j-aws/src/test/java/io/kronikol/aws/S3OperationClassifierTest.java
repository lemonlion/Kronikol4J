package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies S3 classification (path/virtual-hosted style, query params, copy header) matches .NET. */
class S3OperationClassifierTest {

    private static S3OperationInfo classify(String method, String url) {
        return S3OperationClassifier.classify(method, URI.create(url), false);
    }

    @Test
    void objectCrudPathStyle() {
        S3OperationInfo put = classify("PUT", "https://s3.amazonaws.com/mybucket/mykey");
        assertThat(put.operation()).isEqualTo(S3Operation.PUT_OBJECT);
        assertThat(put.bucketName()).isEqualTo("mybucket");
        assertThat(put.keyName()).isEqualTo("mykey");

        assertThat(classify("GET", "https://s3.amazonaws.com/b/k").operation()).isEqualTo(S3Operation.GET_OBJECT);
        assertThat(classify("DELETE", "https://s3.amazonaws.com/b/k").operation())
            .isEqualTo(S3Operation.DELETE_OBJECT);
        assertThat(classify("HEAD", "https://s3.amazonaws.com/b/k").operation()).isEqualTo(S3Operation.HEAD_OBJECT);
    }

    @Test
    void copyObjectRequiresCopySourceHeader() {
        S3OperationInfo copy = S3OperationClassifier.classify(
            "PUT", URI.create("https://s3.amazonaws.com/dest/key"), true);
        assertThat(copy.operation()).isEqualTo(S3Operation.COPY_OBJECT);
    }

    @Test
    void multipartAndTaggingViaQueryParams() {
        assertThat(classify("PUT", "https://s3.amazonaws.com/b/k?partNumber=1&uploadId=abc").operation())
            .isEqualTo(S3Operation.UPLOAD_PART);
        assertThat(classify("POST", "https://s3.amazonaws.com/b/k?uploads").operation())
            .isEqualTo(S3Operation.CREATE_MULTIPART_UPLOAD);
        assertThat(classify("POST", "https://s3.amazonaws.com/b/k?uploadId=abc").operation())
            .isEqualTo(S3Operation.COMPLETE_MULTIPART_UPLOAD);
        assertThat(classify("PUT", "https://s3.amazonaws.com/b/k?tagging").operation())
            .isEqualTo(S3Operation.PUT_OBJECT_TAGGING);
        assertThat(classify("GET", "https://s3.amazonaws.com/b/k?tagging").operation())
            .isEqualTo(S3Operation.GET_OBJECT_TAGGING);
    }

    @Test
    void bucketLevelOperations() {
        assertThat(classify("GET", "https://s3.amazonaws.com/mybucket?list-type=2").operation())
            .isEqualTo(S3Operation.LIST_OBJECTS_V2);
        assertThat(classify("GET", "https://s3.amazonaws.com/mybucket?versions").operation())
            .isEqualTo(S3Operation.LIST_OBJECT_VERSIONS);
        assertThat(classify("POST", "https://s3.amazonaws.com/mybucket?delete").operation())
            .isEqualTo(S3Operation.DELETE_OBJECTS);
        assertThat(classify("PUT", "https://s3.amazonaws.com/mybucket").operation())
            .isEqualTo(S3Operation.CREATE_BUCKET);
        assertThat(classify("DELETE", "https://s3.amazonaws.com/mybucket").operation())
            .isEqualTo(S3Operation.DELETE_BUCKET);
    }

    @Test
    void listBucketsWhenNoBucket() {
        S3OperationInfo info = classify("GET", "https://s3.amazonaws.com/");
        assertThat(info.operation()).isEqualTo(S3Operation.LIST_BUCKETS);
        assertThat(info.bucketName()).isNull();
    }

    @Test
    void virtualHostedStyle() {
        S3OperationInfo info = classify("GET", "https://mybucket.s3.us-east-1.amazonaws.com/path/to/key");
        assertThat(info.operation()).isEqualTo(S3Operation.GET_OBJECT);
        assertThat(info.bucketName()).isEqualTo("mybucket");
        assertThat(info.keyName()).isEqualTo("path/to/key");
    }

    @Test
    void diagramLabel() {
        S3OperationInfo info = classify("PUT", "https://s3.amazonaws.com/b/k");
        assertThat(S3OperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED)).isEqualTo("PutObject");
        assertThat(S3OperationClassifier.getDiagramLabel(info, TrackingVerbosity.RAW)).isNull();
    }
}
