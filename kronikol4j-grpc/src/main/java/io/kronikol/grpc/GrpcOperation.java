package io.kronikol.grpc;

/** Classified gRPC operation types. Java port of the .NET {@code GrpcOperation} enum. */
public enum GrpcOperation {
    UNARY_CALL,
    SERVER_STREAMING_CALL,
    CLIENT_STREAMING_CALL,
    DUPLEX_STREAMING_CALL,
    OTHER
}
