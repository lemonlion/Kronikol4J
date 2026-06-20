package io.kronikol.jdbc;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.TrackingDefaults;
import java.util.function.Supplier;

/**
 * Configuration for JDBC tracking.
 *
 * @param serviceName       the database participant in the diagram (e.g. {@code "OrderDb"}).
 * @param callerName        who is calling (default {@link TrackingDefaults#CALLER_NAME}).
 * @param dependencyCategory category (default {@link DependencyCategories#SQL} -> database shape).
 * @param testInfoFetcher   optional Layer-2 delegate; may be {@code null}.
 */
public record JdbcTrackingOptions(String serviceName, String callerName, String dependencyCategory,
                                  Supplier<TestInfo> testInfoFetcher) {

    public static JdbcTrackingOptions forDatabase(String serviceName) {
        return new JdbcTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME,
            DependencyCategories.SQL, null);
    }
}
