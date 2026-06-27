package io.kronikol.spanner;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.jdbc.SqlTrackingOptions;
import io.kronikol.jdbc.TrackingDataSource;
import javax.sql.DataSource;

/**
 * Google Cloud Spanner tracking — the Java analog of the .NET {@code Kronikol.Extensions.Spanner}
 * (connection/command/transaction wrappers + async stream reader).
 *
 * <p>Cloud Spanner ships a JDBC driver, and the JDBC module's {@link TrackingDataSource} already wraps any
 * {@link DataSource} (proxying {@code Connection}/{@code Statement}/{@code ResultSet} with full two-phase +
 * streaming result capture — the JDBC {@code ResultSet} is the Java analog of Spanner's async stream reader),
 * so no driver-specific wrappers are needed: this is a thin convenience layer applying the Spanner defaults —
 * service name {@code "Spanner"}, the {@link DependencyCategories#SPANNER} category (renders as a
 * {@code database} participant), and the {@code spanner} URI scheme. The shared {@code UnifiedSqlClassifier}
 * already strips Spanner statement hints.
 */
public final class SpannerTracking {

    /** The Spanner service/participant default name. */
    public static final String DEFAULT_SERVICE_NAME = "Spanner";
    /** The Spanner URI scheme. */
    public static final String URI_SCHEME = "spanner";

    private SpannerTracking() {
    }

    /** A fresh options builder pre-set with the Spanner defaults (service name, category, URI scheme). */
    public static SqlTrackingOptions.Builder options() {
        return SqlTrackingOptions.builder()
            .serviceName(DEFAULT_SERVICE_NAME)
            .dependencyCategory(DependencyCategories.SPANNER)
            .uriScheme(URI_SCHEME);
    }

    /** The all-default Spanner tracking options. */
    public static SqlTrackingOptions defaultOptions() {
        return options().build();
    }

    /** Wraps {@code dataSource} for tracking with the default Spanner options. */
    public static DataSource wrap(DataSource dataSource) {
        return TrackingDataSource.wrap(dataSource, defaultOptions());
    }

    /** Wraps {@code dataSource} for tracking with the supplied options (use {@link #options()} as a base). */
    public static DataSource wrap(DataSource dataSource, SqlTrackingOptions options) {
        return TrackingDataSource.wrap(dataSource, options);
    }
}
