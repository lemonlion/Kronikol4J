package io.kronikol.http;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.TrackingDefaults;
import java.util.function.Supplier;

/**
 * Configuration for HTTP tracking.
 *
 * @param serviceName       the diagram participant for the called service.
 * @param callerName        who is calling (default {@link TrackingDefaults#CALLER_NAME}).
 * @param dependencyCategory category (default {@link DependencyCategories#HTTP}).
 * @param testInfoFetcher   optional Layer-2 delegate; may be {@code null}.
 */
public record HttpTrackingOptions(String serviceName, String callerName, String dependencyCategory,
                                  Supplier<TestInfo> testInfoFetcher) {

    public static HttpTrackingOptions forService(String serviceName) {
        return new HttpTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME,
            DependencyCategories.HTTP, null);
    }
}
