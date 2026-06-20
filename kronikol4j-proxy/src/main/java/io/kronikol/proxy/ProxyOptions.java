package io.kronikol.proxy;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.TrackingDefaults;
import java.util.function.Supplier;

/**
 * Configuration for a {@link TrackingProxy}.
 *
 * @param serviceName       the diagram participant for the wrapped service.
 * @param callerName        who is calling (defaults to {@link TrackingDefaults#CALLER_NAME}).
 * @param dependencyCategory optional category (drives shape/colour); may be {@code null}.
 * @param testInfoFetcher   optional framework delegate for identity resolution (Layer 2); may be {@code null}.
 */
public record ProxyOptions(String serviceName, String callerName, String dependencyCategory,
                           Supplier<TestInfo> testInfoFetcher) {

    public static ProxyOptions forService(String serviceName) {
        return new ProxyOptions(serviceName, TrackingDefaults.CALLER_NAME, null, null);
    }

    public ProxyOptions withCategory(String category) {
        return new ProxyOptions(serviceName, callerName, category, testInfoFetcher);
    }

    public ProxyOptions withCallerName(String caller) {
        return new ProxyOptions(serviceName, caller, dependencyCategory, testInfoFetcher);
    }
}
