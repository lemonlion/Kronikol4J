package io.kronikol.bigtable;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.kronikol.bigtable.BigtableInteractionRecorder.Correlation;
import java.util.Optional;

/**
 * A gRPC {@link ClientInterceptor} that auto-captures Cloud Bigtable data calls (ReadRows / MutateRow / …) —
 * the Java analog of the .NET Bigtable SDK hook. The {@code google-cloud-bigtable} data client runs on
 * gax-grpc, so register this on its channel (e.g. via
 * {@code BigtableDataSettings.Builder…setInterceptorProvider(...)} / a custom {@code ChannelConfigurator}).
 *
 * <p>Per call it derives the operation from the gRPC method's bare name (the segment after {@code /} in the
 * full method name — {@code google.bigtable.v2.Bigtable/ReadRows} → {@code ReadRows}, matching
 * {@link BigtableOperationClassifier}), extracts the target table from the request message's
 * {@code getTableName()} (reflectively, so no Bigtable-proto dependency is needed), and delegates to the
 * two-phase {@link BigtableInteractionRecorder} — logging the request when the message is sent and the
 * response (or the gRPC failure) when the call closes. The gRPC dependency is {@code compileOnly}.
 */
public final class KronikolBigtableInterceptor implements ClientInterceptor {

    private final BigtableInteractionRecorder recorder;

    public KronikolBigtableInterceptor(BigtableTrackerOptions options) {
        this.recorder = new BigtableInteractionRecorder(options);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        String bareMethod = bareName(method.getFullMethodName());

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            private Optional<Correlation> correlation = Optional.empty();
            private BigtableOperationInfo op;

            @Override
            public void sendMessage(ReqT message) {
                op = BigtableOperationClassifier.classify(bareMethod, tableName(message), null, null);
                correlation = recorder.logRequest(op, null);
                super.sendMessage(message);
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Listener<RespT> tracking =
                    new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            if (op != null) {
                                correlation.ifPresent(c -> recorder.logResponse(
                                    op, c, status.isOk() ? null : status.getDescription()));
                            }
                            super.onClose(status, trailers);
                        }
                    };
                super.start(tracking, headers);
            }
        };
    }

    /** The segment after the last {@code /} in the gRPC full method name. */
    private static String bareName(String fullMethodName) {
        if (fullMethodName == null) {
            return "";
        }
        int slash = fullMethodName.lastIndexOf('/');
        return slash >= 0 ? fullMethodName.substring(slash + 1) : fullMethodName;
    }

    /** Reads the request message's {@code getTableName()} reflectively (avoids a Bigtable-proto dependency). */
    private static String tableName(Object message) {
        if (message == null) {
            return null;
        }
        try {
            Object value = message.getClass().getMethod("getTableName").invoke(message);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null; // message without a table name (or inaccessible) — leave it unset
        }
    }
}
