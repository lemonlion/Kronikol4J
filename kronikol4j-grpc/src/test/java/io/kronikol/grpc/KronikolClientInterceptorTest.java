package io.kronikol.grpc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.TrackingVerbosity;
import io.kronikol.grpc.GrpcTracking.GrpcTrackingOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@link KronikolClientInterceptor} through hand-built grpc-api fakes (no server) to verify the
 * verbosity wiring: Detailed captures the request/response message payloads; Summarised omits them.
 */
class KronikolClientInterceptorTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    @Test
    void detailedVerbosityCapturesMessagePayloads() {
        List<RequestResponseLog> logs = drive(TrackingVerbosity.DETAILED);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(logs.get(0).content()).isEqualTo("req-body");
        assertThat(logs.get(1).content()).isEqualTo("resp-body");
    }

    @Test
    void summarisedVerbosityOmitsMessagePayloads() {
        List<RequestResponseLog> logs = drive(TrackingVerbosity.SUMMARISED);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).content()).isNull(); // payload omitted at Summarised
        assertThat(logs.get(1).content()).isNull();
    }

    @Test
    void setupPhaseVerbosityOverrideOmitsPayloads() {
        // Base Detailed, but the Setup-phase override = Summarised → message bodies dropped in Setup.
        GrpcTrackingOptions options = new GrpcTrackingOptions(
            "OrderService", "Test", () -> new TestInfo("MyTest", "id-1"), TrackingVerbosity.DETAILED)
            .withSetupVerbosity(TrackingVerbosity.SUMMARISED);
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.SETUP);
        try {
            List<RequestResponseLog> logs = driveWith(options);
            assertThat(logs.get(0).content()).isNull();
            assertThat(logs.get(1).content()).isNull();
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void protobufMessagesAreCapturedAsJson() {
        FakeProtoChannel channel = new FakeProtoChannel();
        GrpcTrackingOptions options = new GrpcTrackingOptions(
            "OrderService", "Test", () -> new TestInfo("MyTest", "id-1"), TrackingVerbosity.DETAILED);

        ClientCall<com.google.protobuf.Type, com.google.protobuf.Type> call =
            new KronikolClientInterceptor(options).interceptCall(protoMethod(), CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
        call.sendMessage(com.google.protobuf.Type.newBuilder().setName("OrderRequest").build());

        channel.call.listener.onMessage(com.google.protobuf.Type.newBuilder().setName("OrderReply").build());
        channel.call.listener.onClose(Status.OK, new Metadata());

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).content()).isEqualTo("{\"name\":\"OrderRequest\"}"); // compact protobuf-JSON
        assertThat(logs.get(1).content()).isEqualTo("{\"name\":\"OrderReply\"}");
    }

    /** Runs one unary call through the interceptor at {@code verbosity}, returns the recorded logs. */
    private static List<RequestResponseLog> drive(TrackingVerbosity verbosity) {
        return driveWith(new GrpcTrackingOptions(
            "OrderService", "Test", () -> new TestInfo("MyTest", "id-1"), verbosity));
    }

    /** Runs one unary call through the interceptor with the given {@code options}, returns the recorded logs. */
    private static List<RequestResponseLog> driveWith(GrpcTrackingOptions options) {
        FakeChannel channel = new FakeChannel();

        ClientCall<String, String> call = new KronikolClientInterceptor(options)
            .interceptCall(method(), CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
        call.sendMessage("req-body");

        channel.call.listener.onMessage("resp-body");
        channel.call.listener.onClose(Status.OK, new Metadata());
        return RequestResponseLogger.getAllLogs();
    }

    private static MethodDescriptor<String, String> method() {
        return MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("orders.OrderService/Checkout")
            .setRequestMarshaller(STRING_MARSHALLER)
            .setResponseMarshaller(STRING_MARSHALLER)
            .build();
    }

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
        new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };

    /** A fake {@link ClientCall} that captures the listener the interceptor installs. */
    private static final class FakeCall extends ClientCall<String, String> {
        private Listener<String> listener;

        @Override
        public void start(Listener<String> responseListener, Metadata headers) {
            this.listener = responseListener;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(String message) {
        }
    }

    private static final class FakeChannel extends Channel {
        private final FakeCall call = new FakeCall();

        @Override
        @SuppressWarnings("unchecked")
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
            MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            return (ClientCall<ReqT, RespT>) call;
        }

        @Override
        public String authority() {
            return "test";
        }
    }

    // --- protobuf-typed fakes (for the proto→JSON capture test) ---

    private static MethodDescriptor<com.google.protobuf.Type, com.google.protobuf.Type> protoMethod() {
        return MethodDescriptor.<com.google.protobuf.Type, com.google.protobuf.Type>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("orders.OrderService/Checkout")
            .setRequestMarshaller(PROTO_MARSHALLER)
            .setResponseMarshaller(PROTO_MARSHALLER)
            .build();
    }

    private static final MethodDescriptor.Marshaller<com.google.protobuf.Type> PROTO_MARSHALLER =
        new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(com.google.protobuf.Type value) {
                return new ByteArrayInputStream(value.toByteArray());
            }

            @Override
            public com.google.protobuf.Type parse(InputStream stream) {
                try {
                    return com.google.protobuf.Type.parseFrom(stream.readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };

    private static final class FakeProtoCall extends ClientCall<com.google.protobuf.Type, com.google.protobuf.Type> {
        private Listener<com.google.protobuf.Type> listener;

        @Override
        public void start(Listener<com.google.protobuf.Type> responseListener, Metadata headers) {
            this.listener = responseListener;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(com.google.protobuf.Type message) {
        }
    }

    private static final class FakeProtoChannel extends Channel {
        private final FakeProtoCall call = new FakeProtoCall();

        @Override
        @SuppressWarnings("unchecked")
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
            MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            return (ClientCall<ReqT, RespT>) call;
        }

        @Override
        public String authority() {
            return "test";
        }
    }
}
