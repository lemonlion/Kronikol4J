package io.kronikol.bigtable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@link KronikolBigtableInterceptor} through grpc-api fakes (no Bigtable) to verify it derives the
 * operation from the gRPC method name, extracts the table from the request's {@code getTableName()}, and emits
 * the classified pair via the two-phase recorder.
 */
class KronikolBigtableInterceptorTest {

    /** A stand-in for a Bigtable request proto — only {@code getTableName()} is read (reflectively). */
    record ReadRowsRequest(String tableName) {
        public String getTableName() {
            return tableName;
        }
    }

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    @Test
    void readRowsCallIsClassifiedAndRecorded() {
        BigtableTrackerOptions options = BigtableTrackerOptions.builder()
            .serviceName("OrdersBt").callerName("Test")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1))
            .build();
        FakeChannel channel = new FakeChannel();

        ClientCall<ReadRowsRequest, String> call = new KronikolBigtableInterceptor(options)
            .interceptCall(method(), CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
        call.sendMessage(new ReadRowsRequest("projects/p/instances/i/tables/orders"));
        channel.call.listener.onClose(Status.OK, new Metadata());

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("ReadRows ← orders"); // classifier label, short table name
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.BIGTABLE);
        assertThat(logs.get(1).type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(req.traceId()).isEqualTo(logs.get(1).traceId());
    }

    private static MethodDescriptor<ReadRowsRequest, String> method() {
        return MethodDescriptor.<ReadRowsRequest, String>newBuilder()
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName("google.bigtable.v2.Bigtable/ReadRows")
            .setRequestMarshaller(REQUEST_MARSHALLER)
            .setResponseMarshaller(STRING_MARSHALLER)
            .build();
    }

    private static final MethodDescriptor.Marshaller<ReadRowsRequest> REQUEST_MARSHALLER =
        new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(ReadRowsRequest value) {
                return new ByteArrayInputStream(value.tableName().getBytes(UTF_8));
            }

            @Override
            public ReadRowsRequest parse(InputStream stream) {
                return new ReadRowsRequest("");
            }
        };

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
        new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                return "";
            }
        };

    private static final class FakeCall extends ClientCall<ReadRowsRequest, String> {
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
        public void sendMessage(ReadRowsRequest message) {
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
            return "bigtable";
        }
    }
}
