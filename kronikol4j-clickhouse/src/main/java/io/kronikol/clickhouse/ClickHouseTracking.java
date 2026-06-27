package io.kronikol.clickhouse;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.jdbc.SqlTrackingOptions;
import io.kronikol.jdbc.TrackingDataSource;
import javax.sql.DataSource;

/**
 * ClickHouse tracking — the Java analog of the .NET {@code Kronikol.Extensions.ClickHouse}
 * ({@code TrackingClickHouseConnection}/{@code Command}/{@code Transaction} + {@code ClickHouseTrackingOptions}).
 *
 * <p>Because Java's JDBC layer is uniform and the JDBC module's {@link TrackingDataSource} already wraps any
 * {@link DataSource} (proxying {@code Connection}/{@code Statement}/{@code ResultSet} with full two-phase +
 * result capture), ClickHouse needs no driver-specific connection subclasses: this is a thin convenience layer
 * that wraps a ClickHouse JDBC {@code DataSource} with ClickHouse defaults — service name {@code "ClickHouse"},
 * the {@link DependencyCategories#CLICK_HOUSE} category (renders as a {@code database} participant), and the
 * {@code clickhouse} URI scheme. The shared {@code UnifiedSqlClassifier} already understands ClickHouse syntax
 * ({@code OPTIMIZE}/{@code RENAME}/{@code ATTACH}/{@code DETACH}/lightweight {@code ALTER … UPDATE/DELETE}).
 */
public final class ClickHouseTracking {

    /** The ClickHouse service/participant default name. */
    public static final String DEFAULT_SERVICE_NAME = "ClickHouse";
    /** The ClickHouse URI scheme. */
    public static final String URI_SCHEME = "clickhouse";

    private ClickHouseTracking() {
    }

    /** A fresh options builder pre-set with the ClickHouse defaults (service name, category, URI scheme). */
    public static SqlTrackingOptions.Builder options() {
        return SqlTrackingOptions.builder()
            .serviceName(DEFAULT_SERVICE_NAME)
            .dependencyCategory(DependencyCategories.CLICK_HOUSE)
            .uriScheme(URI_SCHEME);
    }

    /** The all-default ClickHouse tracking options. */
    public static SqlTrackingOptions defaultOptions() {
        return options().build();
    }

    /** Wraps {@code dataSource} for tracking with the default ClickHouse options. */
    public static DataSource wrap(DataSource dataSource) {
        return TrackingDataSource.wrap(dataSource, defaultOptions());
    }

    /** Wraps {@code dataSource} for tracking with the supplied options (use {@link #options()} as a base). */
    public static DataSource wrap(DataSource dataSource, SqlTrackingOptions options) {
        return TrackingDataSource.wrap(dataSource, options);
    }
}
