package io.kronikol.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.MethodDescriptor.MethodType;
import io.kronikol.core.tracking.TrackingVerbosity;
import org.junit.jupiter.api.Test;

/** Verifies gRPC method-type classification + streaming labels match .NET {@code GrpcOperationClassifier}. */
class GrpcOperationClassifierTest {

    @Test
    void classifiesEachMethodType() {
        assertThat(GrpcOperationClassifier.classify(MethodType.UNARY)).isEqualTo(GrpcOperation.UNARY_CALL);
        assertThat(GrpcOperationClassifier.classify(MethodType.SERVER_STREAMING))
            .isEqualTo(GrpcOperation.SERVER_STREAMING_CALL);
        assertThat(GrpcOperationClassifier.classify(MethodType.CLIENT_STREAMING))
            .isEqualTo(GrpcOperation.CLIENT_STREAMING_CALL);
        assertThat(GrpcOperationClassifier.classify(MethodType.BIDI_STREAMING))
            .isEqualTo(GrpcOperation.DUPLEX_STREAMING_CALL);
        assertThat(GrpcOperationClassifier.classify(MethodType.UNKNOWN)).isEqualTo(GrpcOperation.OTHER);
    }

    @Test
    void detailedLabelsDistinguishStreamingCalls() {
        assertThat(label(GrpcOperation.UNARY_CALL, TrackingVerbosity.DETAILED)).isEqualTo("Checkout");
        assertThat(label(GrpcOperation.SERVER_STREAMING_CALL, TrackingVerbosity.DETAILED))
            .isEqualTo("Checkout (server-stream)");
        assertThat(label(GrpcOperation.CLIENT_STREAMING_CALL, TrackingVerbosity.DETAILED))
            .isEqualTo("Checkout (client-stream)");
        assertThat(label(GrpcOperation.DUPLEX_STREAMING_CALL, TrackingVerbosity.DETAILED))
            .isEqualTo("Checkout (duplex-stream)");
    }

    @Test
    void rawAndSummarisedLabels() {
        assertThat(label(GrpcOperation.SERVER_STREAMING_CALL, TrackingVerbosity.RAW))
            .isEqualTo("orders.OrderService/Checkout [SERVER_STREAMING_CALL]");
        assertThat(label(GrpcOperation.SERVER_STREAMING_CALL, TrackingVerbosity.SUMMARISED)).isEqualTo("Checkout");
    }

    private static String label(GrpcOperation op, TrackingVerbosity v) {
        return GrpcOperationClassifier.getDiagramLabel(op, "Checkout", "orders.OrderService/Checkout", v);
    }
}
