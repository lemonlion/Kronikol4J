package io.kronikol.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.grpc.GrpcTracking.GrpcTrackingOptions;
import io.kronikol.junit5.KronikolExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class GrpcTrackingTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void extractsTheMethodSegmentFromTheFullName() {
        assertThat(GrpcTracking.methodName("orders.OrderService/Checkout")).isEqualTo("Checkout");
        assertThat(GrpcTracking.methodName("Bare")).isEqualTo("Bare");
    }

    @Test
    void recordsAGrpcCallAndRendersIt() {
        GrpcTracking.record(GrpcTrackingOptions.forService("OrderService"),
            "orders.OrderService/Checkout", "{\"item\":\"egg\"}", "{\"ok\":true}", StatusCode.of("OK"));

        var logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("Checkout");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("entity \"OrderService\" as orderService")
            .contains("test -> orderService: Checkout: /")
            .contains("orderService --> test: OK");
    }
}
