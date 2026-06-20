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
import io.kronikol.core.tracking.StatusCode;
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

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            private String requestSummary;

            @Override
            public void sendMessage(ReqT message) {
                requestSummary = String.valueOf(message);
                super.sendMessage(message);
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
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
                            StatusCode code = status.isOk()
                                ? StatusCode.of("OK")
                                : StatusCode.of(status.getCode().name());
                            GrpcTracking.record(options, fullMethodName, requestSummary, responseSummary, code);
                            super.onClose(status, trailers);
                        }
                    };
                super.start(tracking, headers);
            }
        };
    }
}
