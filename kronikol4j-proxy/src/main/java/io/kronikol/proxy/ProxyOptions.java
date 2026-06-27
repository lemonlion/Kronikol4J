package io.kronikol.proxy;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.TrackingDefaults;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Configuration for a {@link TrackingProxy}.
 *
 * @param serviceName        the diagram participant for the wrapped service.
 * @param callerName         who is calling (defaults to {@link TrackingDefaults#CALLER_NAME}).
 * @param dependencyCategory optional category (drives shape/colour); may be {@code null}.
 * @param testInfoFetcher    optional framework delegate for identity resolution (Layer 2); may be {@code null}.
 * @param uriScheme          the diagram URI prefix (default {@code "proxy://local"}); the interface + method
 *                           are appended.
 * @param logMode            {@link TrackingLogMode#IMMEDIATE} (default) or {@link TrackingLogMode#DEFERRED}.
 * @param ids                the trace/request-response id source (the determinism seam; default random).
 * @param payloadSerializer  serialises a call argument / return value to note content (default
 *                           {@code String.valueOf}; plug a {@code TrackingSafeSerializer}-backed function for
 *                           JSON).
 */
public record ProxyOptions(String serviceName, String callerName, String dependencyCategory,
                           Supplier<TestInfo> testInfoFetcher, String uriScheme, TrackingLogMode logMode,
                           IdGenerator ids, Function<Object, String> payloadSerializer) {

    /** The default serialiser: the value's {@code String.valueOf} (preserving the original proxy behaviour). */
    public static final Function<Object, String> DEFAULT_SERIALIZER =
        value -> value == null ? null : String.valueOf(value);

    public static ProxyOptions forService(String serviceName) {
        return new ProxyOptions(serviceName, TrackingDefaults.CALLER_NAME, null, null,
            "proxy://local", TrackingLogMode.IMMEDIATE, IdGenerator.random(), DEFAULT_SERIALIZER);
    }

    public ProxyOptions withCategory(String category) {
        return new ProxyOptions(serviceName, callerName, category, testInfoFetcher,
            uriScheme, logMode, ids, payloadSerializer);
    }

    public ProxyOptions withCallerName(String caller) {
        return new ProxyOptions(serviceName, caller, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, ids, payloadSerializer);
    }

    public ProxyOptions withTestInfoFetcher(Supplier<TestInfo> fetcher) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, fetcher,
            uriScheme, logMode, ids, payloadSerializer);
    }

    public ProxyOptions withUriScheme(String scheme) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            scheme, logMode, ids, payloadSerializer);
    }

    public ProxyOptions withLogMode(TrackingLogMode mode) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, mode, ids, payloadSerializer);
    }

    public ProxyOptions withIds(IdGenerator idGenerator) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, idGenerator, payloadSerializer);
    }

    public ProxyOptions withSerializer(Function<Object, String> serializer) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, ids, serializer);
    }
}
