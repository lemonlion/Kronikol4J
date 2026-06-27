package io.kronikol.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.sql.UnifiedSqlOperation;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link SqlTrackingOptions} surface — the Java analog of the .NET {@code SqlTrackingOptionsBase}.
 * Confirms all defaults match the .NET record, the builder round-trips every field, and the documented
 * {@code maxResponseRows} negative→0 clamp.
 */
class SqlTrackingOptionsTest {

    @Test
    void defaultsMatchDotNet() {
        SqlTrackingOptions o = SqlTrackingOptions.builder().build();
        assertThat(o.serviceName()).isEqualTo("Database");
        assertThat(o.callerName()).isEqualTo(TrackingDefaults.CALLER_NAME);
        assertThat(o.dependencyCategory()).isEqualTo(DependencyCategories.SQL);
        assertThat(o.uriScheme()).isEqualTo("sql");
        assertThat(o.verbosity()).isEqualTo(TrackingVerbosity.DETAILED);
        assertThat(o.setupVerbosity()).isNull();
        assertThat(o.actionVerbosity()).isNull();
        assertThat(o.trackDuringSetup()).isTrue();
        assertThat(o.trackDuringAction()).isTrue();
        assertThat(o.logSqlText()).isTrue();
        assertThat(o.logParameters()).isFalse();
        assertThat(o.logResponseContent()).isTrue();
        assertThat(o.excludedOperations()).isEmpty();
        assertThat(o.maxResponseRows()).isEqualTo(10);
        assertThat(o.maxValueDisplayLength()).isEqualTo(500);
        assertThat(o.responseDetail()).isEqualTo(SqlResponseDetail.ROW_COUNT_AND_COLUMNS);
        assertThat(o.testInfoFetcher()).isNull();
        assertThat(o.ids()).isNotNull();
    }

    @Test
    void builderRoundTripsEveryField() {
        SqlTrackingOptions o = SqlTrackingOptions.builder()
            .serviceName("OrderDb").callerName("api").dependencyCategory("PostgreSQL").uriScheme("postgresql")
            .verbosity(TrackingVerbosity.RAW)
            .setupVerbosity(TrackingVerbosity.SUMMARISED).actionVerbosity(TrackingVerbosity.RAW)
            .trackDuringSetup(false).trackDuringAction(false)
            .logSqlText(false).logParameters(true).logResponseContent(false)
            .excludedOperations(Set.of(UnifiedSqlOperation.SELECT))
            .maxResponseRows(25).maxValueDisplayLength(64)
            .responseDetail(SqlResponseDetail.FULL_ROWS)
            .build();

        assertThat(o.serviceName()).isEqualTo("OrderDb");
        assertThat(o.callerName()).isEqualTo("api");
        assertThat(o.dependencyCategory()).isEqualTo("PostgreSQL");
        assertThat(o.uriScheme()).isEqualTo("postgresql");
        assertThat(o.verbosity()).isEqualTo(TrackingVerbosity.RAW);
        assertThat(o.setupVerbosity()).isEqualTo(TrackingVerbosity.SUMMARISED);
        assertThat(o.actionVerbosity()).isEqualTo(TrackingVerbosity.RAW);
        assertThat(o.trackDuringSetup()).isFalse();
        assertThat(o.trackDuringAction()).isFalse();
        assertThat(o.logSqlText()).isFalse();
        assertThat(o.logParameters()).isTrue();
        assertThat(o.logResponseContent()).isFalse();
        assertThat(o.excludedOperations()).containsExactly(UnifiedSqlOperation.SELECT);
        assertThat(o.maxResponseRows()).isEqualTo(25);
        assertThat(o.maxValueDisplayLength()).isEqualTo(64);
        assertThat(o.responseDetail()).isEqualTo(SqlResponseDetail.FULL_ROWS);
    }

    @Test
    void negativeMaxResponseRowsClampsToZero() {
        assertThat(SqlTrackingOptions.builder().maxResponseRows(-5).build().maxResponseRows()).isZero();
        assertThat(SqlTrackingOptions.builder().maxResponseRows(0).build().maxResponseRows()).isZero();
    }

    @Test
    void excludedOperationsIsImmutableSnapshot() {
        java.util.Set<UnifiedSqlOperation> mutable = new java.util.HashSet<>(Set.of(UnifiedSqlOperation.INSERT));
        SqlTrackingOptions o = SqlTrackingOptions.builder().excludedOperations(mutable).build();
        mutable.add(UnifiedSqlOperation.DELETE);
        assertThat(o.excludedOperations()).containsExactly(UnifiedSqlOperation.INSERT);
    }
}
