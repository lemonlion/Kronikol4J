package io.kronikol.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Field;
import com.google.protobuf.Type;
import org.junit.jupiter.api.Test;

/**
 * Verifies the gRPC message formatter renders protobuf messages as compact protobuf-JSON (matching .NET's
 * {@code JsonFormatter.Default.Format}) and falls back to {@code toString()} otherwise. Uses the well-known
 * {@code google.protobuf.Type}/{@code Field} messages (shipped in protobuf-java) so no codegen is needed.
 */
class GrpcMessageFormatterTest {

    @Test
    void protobufMessageRendersAsCompactCamelCaseJson() {
        Type message = Type.newBuilder().setName("OrderRequest").build();
        // Compact (no insignificant whitespace), default values omitted — the .NET JsonFormatter.Default form.
        assertThat(GrpcMessageFormatter.format(message)).isEqualTo("{\"name\":\"OrderRequest\"}");
    }

    @Test
    void protobufFieldNamesAreCamelCasedAndDefaultsOmitted() {
        // Field has snake_case proto fields (number, type_url); JSON mapping camelCases and omits defaults.
        Field message = Field.newBuilder().setName("orderId").setNumber(1).setTypeUrl("type.example/Order").build();
        assertThat(GrpcMessageFormatter.format(message))
            .isEqualTo("{\"number\":1,\"name\":\"orderId\",\"typeUrl\":\"type.example/Order\"}");
    }

    @Test
    void nonProtobufMessageFallsBackToToString() {
        assertThat(GrpcMessageFormatter.format("plain-body")).isEqualTo("plain-body");
        assertThat(GrpcMessageFormatter.format(42)).isEqualTo("42");
    }

    @Test
    void nullMessageRendersAsNull() {
        assertThat(GrpcMessageFormatter.format(null)).isNull();
    }
}
