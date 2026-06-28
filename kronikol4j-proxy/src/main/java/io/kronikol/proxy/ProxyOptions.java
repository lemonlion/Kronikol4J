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
 * @param trackDuringSetup   track calls made during the Setup phase (the .NET {@code TrackDuringSetup}, default {@code true}).
 * @param trackDuringAction  track calls made during the Action phase (the .NET {@code TrackDuringAction}, default {@code true}).
 */
public record ProxyOptions(String serviceName, String callerName, String dependencyCategory,
                           Supplier<TestInfo> testInfoFetcher, String uriScheme, TrackingLogMode logMode,
                           IdGenerator ids, Function<Object, String> payloadSerializer,
                           boolean trackDuringSetup, boolean trackDuringAction, String activitySourceName) {

    /** The default serialiser: the value's {@code String.valueOf} (preserving the original proxy behaviour). */
    public static final Function<Object, String> DEFAULT_SERIALIZER =
        value -> value == null ? null : String.valueOf(value);

    /** Ten-arg shape (no OTel activity source) — back-compatible. */
    public ProxyOptions(String serviceName, String callerName, String dependencyCategory,
                        Supplier<TestInfo> testInfoFetcher, String uriScheme, TrackingLogMode logMode,
                        IdGenerator ids, Function<Object, String> payloadSerializer,
                        boolean trackDuringSetup, boolean trackDuringAction) {
        this(serviceName, callerName, dependencyCategory, testInfoFetcher, uriScheme, logMode, ids,
            payloadSerializer, trackDuringSetup, trackDuringAction, null);
    }

    /** Eight-arg shape (both phases tracked) — the back-compatible constructor. */
    public ProxyOptions(String serviceName, String callerName, String dependencyCategory,
                        Supplier<TestInfo> testInfoFetcher, String uriScheme, TrackingLogMode logMode,
                        IdGenerator ids, Function<Object, String> payloadSerializer) {
        this(serviceName, callerName, dependencyCategory, testInfoFetcher, uriScheme, logMode, ids,
            payloadSerializer, true, true, null);
    }

    public static ProxyOptions forService(String serviceName) {
        return new ProxyOptions(serviceName, TrackingDefaults.CALLER_NAME, null, null,
            "proxy://local", TrackingLogMode.IMMEDIATE, IdGenerator.random(), DEFAULT_SERIALIZER,
            true, true, null);
    }

    public ProxyOptions withCategory(String category) {
        return new ProxyOptions(serviceName, callerName, category, testInfoFetcher,
            uriScheme, logMode, ids, payloadSerializer, trackDuringSetup, trackDuringAction, activitySourceName);
    }

    public ProxyOptions withCallerName(String caller) {
        return new ProxyOptions(serviceName, caller, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, ids, payloadSerializer, trackDuringSetup, trackDuringAction, activitySourceName);
    }

    public ProxyOptions withTestInfoFetcher(Supplier<TestInfo> fetcher) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, fetcher,
            uriScheme, logMode, ids, payloadSerializer, trackDuringSetup, trackDuringAction, activitySourceName);
    }

    public ProxyOptions withUriScheme(String scheme) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            scheme, logMode, ids, payloadSerializer, trackDuringSetup, trackDuringAction, activitySourceName);
    }

    public ProxyOptions withLogMode(TrackingLogMode mode) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, mode, ids, payloadSerializer, trackDuringSetup, trackDuringAction, activitySourceName);
    }

    public ProxyOptions withIds(IdGenerator idGenerator) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, idGenerator, payloadSerializer, trackDuringSetup, trackDuringAction,
            activitySourceName);
    }

    public ProxyOptions withSerializer(Function<Object, String> serializer) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, ids, serializer, trackDuringSetup, trackDuringAction, activitySourceName);
    }

    /** A copy that (does not) track during the Setup phase (the .NET {@code TrackDuringSetup}). */
    public ProxyOptions withTrackDuringSetup(boolean value) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, ids, payloadSerializer, value, trackDuringAction, activitySourceName);
    }

    /** A copy that (does not) track during the Action phase (the .NET {@code TrackDuringAction}). */
    public ProxyOptions withTrackDuringAction(boolean value) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, ids, payloadSerializer, trackDuringSetup, value, activitySourceName);
    }

    /** A copy that emits an OpenTelemetry span per tracked call from the named tracer (the .NET
     *  {@code ActivitySourceName} — feeds the InternalFlow span capture). {@code null} disables span emission. */
    public ProxyOptions withActivitySourceName(String value) {
        return new ProxyOptions(serviceName, callerName, dependencyCategory, testInfoFetcher,
            uriScheme, logMode, ids, payloadSerializer, trackDuringSetup, trackDuringAction, value);
    }
}
