package io.kronikol.grpc;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

/**
 * Renders a gRPC message for the diagram note — the Java analog of the .NET interceptor's
 * {@code SerializeMessage}. Protobuf messages are rendered as compact JSON (matching .NET's
 * {@code JsonFormatter.Default.Format(IMessage)}); anything else falls back to {@code toString()}.
 *
 * <p>The protobuf JSON mapping is a cross-runtime spec (camelCase field names, default values omitted,
 * int64/uint64 as strings, enums as names), and {@link JsonFormat.Printer#omittingInsignificantWhitespace()}
 * produces the same single-line compact form as .NET's {@code JsonFormatter.Default}. Protobuf is referenced
 * reflectively at the type-check boundary only (it is {@code compileOnly}); a non-protobuf classpath never
 * reaches the proto branch.
 */
final class GrpcMessageFormatter {

    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();

    private GrpcMessageFormatter() {
    }

    /** The message rendered as compact protobuf-JSON, or its {@code toString()} when not a protobuf message. */
    static String format(Object message) {
        if (message == null) {
            return null;
        }
        if (message instanceof MessageOrBuilder proto) {
            try {
                return PRINTER.print(proto);
            } catch (Exception e) {
                // e.g. an Any without a type registry — fall back to the proto text form rather than fail.
                return String.valueOf(message);
            }
        }
        return String.valueOf(message);
    }
}
