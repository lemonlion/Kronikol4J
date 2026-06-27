package io.kronikol.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import io.kronikol.core.tracking.StatusCode;
import org.junit.jupiter.api.Test;

/** Verifies the gRPC→HTTP status mapping matches .NET {@code MapGrpcStatusToHttp}. */
class GrpcStatusMappingTest {

    @Test
    void mapsKnownStatuses() {
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.OK)).isEqualTo(StatusCode.of(200));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.NOT_FOUND)).isEqualTo(StatusCode.of(404));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.PERMISSION_DENIED)).isEqualTo(StatusCode.of(403));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.UNAUTHENTICATED)).isEqualTo(StatusCode.of(401));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.INVALID_ARGUMENT)).isEqualTo(StatusCode.of(400));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.DEADLINE_EXCEEDED)).isEqualTo(StatusCode.of(408));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.CANCELLED)).isEqualTo(StatusCode.of(408));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.ALREADY_EXISTS)).isEqualTo(StatusCode.of(409));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.RESOURCE_EXHAUSTED)).isEqualTo(StatusCode.of(429));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.UNAVAILABLE)).isEqualTo(StatusCode.of(503));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.UNIMPLEMENTED)).isEqualTo(StatusCode.of(501));
    }

    @Test
    void unmappedStatusesFallBackTo500() {
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.INTERNAL)).isEqualTo(StatusCode.of(500));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.UNKNOWN)).isEqualTo(StatusCode.of(500));
        assertThat(GrpcStatusMapping.toHttpStatus(Status.Code.ABORTED)).isEqualTo(StatusCode.of(500));
    }
}
