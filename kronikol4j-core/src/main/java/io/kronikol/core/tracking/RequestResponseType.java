package io.kronikol.core.tracking;

/**
 * Whether a logged interaction half is the request or the response.
 * A request/response pair shares the same {@code traceId} and {@code requestResponseId}.
 *
 * <p>Mirrors the .NET {@code RequestResponseType} enum.
 */
public enum RequestResponseType {
    REQUEST,
    RESPONSE
}
