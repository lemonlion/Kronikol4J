package io.kronikol.grpc;

import io.grpc.Status;
import io.kronikol.core.tracking.StatusCode;

/**
 * Maps a gRPC {@link Status.Code} to the HTTP status shown on the response arrow, matching the .NET
 * {@code GrpcTrackingInterceptor.MapGrpcStatusToHttp}. (gRPC is HTTP/2-based, so the diagram presents the
 * familiar HTTP status alongside the other tracked calls.)
 */
public final class GrpcStatusMapping {

    private GrpcStatusMapping() {
    }

    /** The HTTP {@link StatusCode} for a gRPC status code. */
    public static StatusCode toHttpStatus(Status.Code grpcStatus) {
        return StatusCode.of(switch (grpcStatus) {
            case OK -> 200;
            case NOT_FOUND -> 404;
            case PERMISSION_DENIED -> 403;
            case UNAUTHENTICATED -> 401;
            case INVALID_ARGUMENT -> 400;
            case DEADLINE_EXCEEDED, CANCELLED -> 408;
            case ALREADY_EXISTS -> 409;
            case RESOURCE_EXHAUSTED -> 429;
            case UNAVAILABLE -> 503;
            case UNIMPLEMENTED -> 501;
            default -> 500;
        });
    }
}
