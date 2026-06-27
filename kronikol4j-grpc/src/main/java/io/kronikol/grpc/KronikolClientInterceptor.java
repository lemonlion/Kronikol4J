package io.kronikol.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
import io.kronikol.core.tracking.W3CTraceparent;
import io.kronikol.grpc.GrpcTracking.GrpcTrackingOptions;

/**
 * A gRPC {@link ClientInterceptor} that records each call as a tracked interaction (plan §3.4 —
 * nearly 1:1 with the .NET gRPC interceptors). Register on a channel:
 *
 * <pre>{@code ManagedChannelBuilder.forTarget(target)
 *         .intercept(new KronikolClientInterceptor(GrpcTrackingOptions.forService("OrderService")))
 *         .build();}</pre>
 *
 * Captures the request (last message sent) and response (last message received) and the final
 * {@link Status}, then delegates to {@link GrpcTracking} when the call closes.
 */
public final class KronikolClientInterceptor implements ClientInterceptor {

    private final GrpcTrackingOptions options;

    public KronikolClientInterceptor(GrpcTrackingOptions options) {
        this.options = options;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        String fullMethodName = method.getFullMethodName();
        // Classify the call type so streaming calls get distinct labels (server-/client-/duplex-stream).
        GrpcOperation operation = GrpcOperationClassifier.classify(method.getType());
        String methodLabel = GrpcOperationClassifier.getDiagramLabel(
            operation, GrpcTracking.methodName(fullMethodName), fullMethodName, TrackingVerbosity.DETAILED);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            private String requestSummary;

            @Override
            public void sendMessage(ReqT message) {
                requestSummary = String.valueOf(message);
                super.sendMessage(message);
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Inject a W3C traceparent so a downstream tracked service joins the trace.
                W3CTraceparent traceparent = W3CTraceparent.generate(IdGenerator.random());
                headers.put(TRACEPARENT, traceparent.header());

                Listener<RespT> tracking =
                    new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                        private String responseSummary;

                        @Override
                        public void onMessage(RespT message) {
                            responseSummary = String.valueOf(message);
                            super.onMessage(message);
                        }

                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            StatusCode code = GrpcStatusMapping.toHttpStatus(status.getCode());
                            GrpcTracking.record(options, fullMethodName, methodLabel,
                                requestSummary, responseSummary, code);
                            super.onClose(status, trailers);
                        }
                    };
                super.start(tracking, headers);
            }
        };
    }

    /** Metadata key for the W3C {@code traceparent} header. */
    private static final Metadata.Key<String> TRACEPARENT =
        Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);
}
