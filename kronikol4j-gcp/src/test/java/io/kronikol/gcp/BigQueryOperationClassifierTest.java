package io.kronikol.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Verifies BigQuery classification (path segment routing) matches the .NET classifier. */
class BigQueryOperationClassifierTest {

    private static final String BASE = "https://bigquery.googleapis.com/bigquery/v2/projects/myproj";

    private static BigQueryOperationInfo classify(String method, String resourcePath) {
        return BigQueryOperationClassifier.classify(method, URI.create(BASE + resourcePath));
    }

    @Test
    void query() {
        BigQueryOperationInfo info = classify("POST", "/queries");
        assertThat(info.operation()).isEqualTo(BigQueryOperation.QUERY);
        assertThat(info.projectId()).isEqualTo("myproj");
        assertThat(info.resourceType()).isEqualTo("query");
    }

    @Test
    void datasets() {
        assertThat(classify("GET", "/datasets").operation()).isEqualTo(BigQueryOperation.LIST);
        assertThat(classify("POST", "/datasets").operation()).isEqualTo(BigQueryOperation.CREATE);
        BigQueryOperationInfo read = classify("GET", "/datasets/ds1");
        assertThat(read.operation()).isEqualTo(BigQueryOperation.READ);
        assertThat(read.datasetId()).isEqualTo("ds1");
        assertThat(classify("DELETE", "/datasets/ds1").operation()).isEqualTo(BigQueryOperation.DELETE);
        assertThat(classify("PATCH", "/datasets/ds1").operation()).isEqualTo(BigQueryOperation.UPDATE);
    }

    @Test
    void tables() {
        assertThat(classify("GET", "/datasets/ds1/tables").operation()).isEqualTo(BigQueryOperation.LIST);
        assertThat(classify("POST", "/datasets/ds1/tables").operation()).isEqualTo(BigQueryOperation.CREATE);
        assertThat(classify("GET", "/datasets/ds1/tables/t1").operation()).isEqualTo(BigQueryOperation.READ);

        BigQueryOperationInfo insert = classify("POST", "/datasets/ds1/tables/t1/insertAll");
        assertThat(insert.operation()).isEqualTo(BigQueryOperation.INSERT);
        assertThat(insert.resourceName()).isEqualTo("t1");

        BigQueryOperationInfo data = classify("GET", "/datasets/ds1/tables/t1/data");
        assertThat(data.operation()).isEqualTo(BigQueryOperation.LIST);
        assertThat(data.resourceType()).isEqualTo("tabledata");
    }

    @Test
    void jobs() {
        assertThat(classify("POST", "/jobs").operation()).isEqualTo(BigQueryOperation.CREATE);
        assertThat(classify("GET", "/jobs/job1").operation()).isEqualTo(BigQueryOperation.READ);
        assertThat(classify("POST", "/jobs/job1/cancel").operation()).isEqualTo(BigQueryOperation.CANCEL);
        assertThat(classify("POST", "/jobs/job1/delete").operation()).isEqualTo(BigQueryOperation.DELETE);
    }

    @Test
    void uploadPrefixAndNonBigQueryPath() {
        BigQueryOperationInfo upload = BigQueryOperationClassifier.classify("POST",
            URI.create("https://bigquery.googleapis.com/upload/bigquery/v2/projects/myproj/jobs"));
        assertThat(upload.operation()).isEqualTo(BigQueryOperation.CREATE);

        assertThat(BigQueryOperationClassifier.classify("GET", URI.create("https://example.com/other"))
            .operation()).isEqualTo(BigQueryOperation.OTHER);
    }

    @Test
    void diagramLabel() {
        BigQueryOperationInfo info = classify("POST", "/queries");
        assertThat(BigQueryOperationClassifier.getDiagramLabel(info, TrackingVerbosity.DETAILED)).isEqualTo("Query");
        assertThat(BigQueryOperationClassifier.getDiagramLabel(info, TrackingVerbosity.RAW)).isNull();
    }
}
