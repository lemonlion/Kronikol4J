package io.kronikol.grpc;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.function.Supplier;

/**
 * Records a gRPC call as a tracked interaction. The reusable core that
 * {@link KronikolClientInterceptor} delegates to — testable without gRPC on the classpath.
 */
public final class GrpcTracking {

    private static final URI GRPC_URI = URI.create("grpc://service/");

    private GrpcTracking() {
    }

    /**
     * @param fullMethodName the gRPC full method name, e.g. {@code "orders.OrderService/Checkout"}.
     */
    public static void record(GrpcTrackingOptions options, String fullMethodName,
                              String request, String response, StatusCode status) {
        record(options, fullMethodName, methodName(fullMethodName), request, response, status);
    }

    /**
     * Records a gRPC call with an explicit method label (e.g. {@code "Subscribe (server-stream)"} from
     * {@link GrpcOperationClassifier}), so streaming call types are distinguished in the diagram.
     */
    public static void record(GrpcTrackingOptions options, String fullMethodName, String methodLabel,
                              String request, String response, StatusCode status) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.GRPC, Method.of(methodLabel), GRPC_URI,
            request, status, response);
    }

    /** The method segment of a gRPC full method name. */
    static String methodName(String fullMethodName) {
        if (fullMethodName == null) {
            return "RPC";
        }
        int slash = fullMethodName.lastIndexOf('/');
        return slash >= 0 ? fullMethodName.substring(slash + 1) : fullMethodName;
    }

    /** Configuration for gRPC tracking. */
    public record GrpcTrackingOptions(String serviceName, String callerName,
                                      Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                      boolean trackDuringSetup, boolean trackDuringAction,
                                      TrackingVerbosity setupVerbosity, TrackingVerbosity actionVerbosity) {

        public GrpcTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Three-arg shape (default verbosity, both phases tracked) — back-compatible. */
        public GrpcTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** Four-arg shape (both phases tracked) — back-compatible. */
        public GrpcTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                   TrackingVerbosity verbosity) {
            this(serviceName, callerName, testInfoFetcher, verbosity, true, true, null, null);
        }

        /** Six-arg shape (no per-phase verbosity overrides) — back-compatible. */
        public GrpcTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                   TrackingVerbosity verbosity, boolean trackDuringSetup,
                                   boolean trackDuringAction) {
            this(serviceName, callerName, testInfoFetcher, verbosity, trackDuringSetup, trackDuringAction,
                null, null);
        }

        public static GrpcTrackingOptions forService(String serviceName) {
            return new GrpcTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** A copy with the given verbosity (Summarised omits the request/response message payloads). */
        public GrpcTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new GrpcTrackingOptions(serviceName, callerName, testInfoFetcher, value,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity);
        }

        /** A copy that (does not) track during the Setup phase (the .NET {@code TrackDuringSetup}). */
        public GrpcTrackingOptions withTrackDuringSetup(boolean value) {
            return new GrpcTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                value, trackDuringAction, setupVerbosity, actionVerbosity);
        }

        /** A copy that (does not) track during the Action phase (the .NET {@code TrackDuringAction}). */
        public GrpcTrackingOptions withTrackDuringAction(boolean value) {
            return new GrpcTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, value, setupVerbosity, actionVerbosity);
        }

        /** A copy with a Setup-phase verbosity override (the .NET {@code SetupVerbosity}; {@code null} = base). */
        public GrpcTrackingOptions withSetupVerbosity(TrackingVerbosity value) {
            return new GrpcTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, value, actionVerbosity);
        }

        /** A copy with an Action-phase verbosity override (the .NET {@code ActionVerbosity}; {@code null} = base). */
        public GrpcTrackingOptions withActionVerbosity(TrackingVerbosity value) {
            return new GrpcTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, value);
        }
    }
}
