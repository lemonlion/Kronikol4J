package io.kronikol.grpc;

import io.grpc.MethodDescriptor.MethodType;
import io.kronikol.core.tracking.TrackingVerbosity;

/**
 * Classifies a gRPC method by its {@link MethodType} (unary / server-, client-, duplex-streaming) and builds
 * the diagram label. Java port of the .NET {@code GrpcOperationClassifier}. This is what extends gRPC
 * tracking beyond unary: the streaming call types get distinct {@code (server-stream)} / {@code (client-stream)}
 * / {@code (duplex-stream)} labels.
 */
public final class GrpcOperationClassifier {

    private GrpcOperationClassifier() {
    }

    /** Classifies a gRPC method type into an operation. */
    public static GrpcOperation classify(MethodType methodType) {
        return switch (methodType) {
            case UNARY -> GrpcOperation.UNARY_CALL;
            case SERVER_STREAMING -> GrpcOperation.SERVER_STREAMING_CALL;
            case CLIENT_STREAMING -> GrpcOperation.CLIENT_STREAMING_CALL;
            case BIDI_STREAMING -> GrpcOperation.DUPLEX_STREAMING_CALL;
            default -> GrpcOperation.OTHER;
        };
    }

    /** The diagram label for a classified call. {@code methodName} is the method segment, {@code fullName} the full path. */
    public static String getDiagramLabel(GrpcOperation operation, String methodName, String fullName,
                                         TrackingVerbosity verbosity) {
        String name = methodName != null ? methodName : "Call";
        return switch (verbosity) {
            case RAW -> (fullName != null ? fullName : name) + " [" + operation + "]";
            case DETAILED -> switch (operation) {
                case SERVER_STREAMING_CALL -> name + " (server-stream)";
                case CLIENT_STREAMING_CALL -> name + " (client-stream)";
                case DUPLEX_STREAMING_CALL -> name + " (duplex-stream)";
                default -> name;
            };
            case SUMMARISED -> name;
        };
    }
}
