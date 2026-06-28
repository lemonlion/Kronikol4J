package io.kronikol.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.gcp.GcpTracking.GcpTrackingOptions;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.junit5.KronikolExtension;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies the GCP HTTP router ({@link GcpHttpTracking}) detects BigQuery / Cloud Storage from the request
 * path and emits via the classifier-driven recorders with the right clean URI + category — the .NET
 * {@code BigQueryTrackingMessageHandler}/{@code CloudStorageTrackingMessageHandler} behaviour. Uses the
 * ambient {@link KronikolExtension} identity.
 */
@ExtendWith(KronikolExtension.class)
class GcpHttpTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    private static GcpTrackingOptions opts() {
        return GcpTrackingOptions.forService("Gcp");
    }

    @Test
    void bigQueryTableGetIsClassifiedWithCleanPathUri() {
        GcpHttpTracking.track(opts(), "GET",
            URI.create("https://bigquery.googleapis.com/bigquery/v2/projects/p/datasets/d/tables/t"), 200);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.method().value()).isEqualTo("Read");
        assertThat(req.uri().toString()).isEqualTo("https://bigquery.googleapis.com/d/table/t"); // rewritten path
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.BIG_QUERY);
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of(200));
    }

    @Test
    void cloudStorageObjectIsClassifiedWithGcsUri() {
        GcpHttpTracking.track(opts(), "GET",
            URI.create("https://storage.googleapis.com/storage/v1/b/my-bucket/o/photo.jpg"), 200);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        // GET object (no alt=media) → metadata; Detailed label carries the directional arrow + bucket/object.
        assertThat(req.method().value()).isEqualTo("GetMetadata → my-bucket/photo.jpg");
        assertThat(req.uri().toString()).isEqualTo("gcs:///my-bucket/photo.jpg");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.CLOUD_STORAGE);
    }

    @Test
    void cloudStorageDownloadIsClassified() {
        GcpHttpTracking.track(opts(), "GET",
            URI.create("https://storage.googleapis.com/storage/v1/b/my-bucket/o/photo.jpg?alt=media"), 200);

        assertThat(RequestResponseLogger.getAllLogs().get(0).method().value())
            .isEqualTo("Download → my-bucket/photo.jpg");
    }

    @Test
    void nonGcpPathIsNotTracked() {
        GcpHttpTracking.track(opts(), "GET", URI.create("https://example.com/api/data"), 200);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
