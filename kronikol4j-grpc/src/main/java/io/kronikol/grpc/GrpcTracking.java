package io.kronikol.grpc;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
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
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.GRPC, Method.of(methodName(fullMethodName)), GRPC_URI,
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
                                      Supplier<TestInfo> testInfoFetcher) {
        public static GrpcTrackingOptions forService(String serviceName) {
            return new GrpcTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null);
        }
    }
}
