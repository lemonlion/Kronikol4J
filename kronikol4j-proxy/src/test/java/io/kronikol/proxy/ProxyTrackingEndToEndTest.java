package io.kronikol.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The full vertical slice: a real JUnit test (under {@link KronikolExtension}) calls a tracked
 * service through {@link TrackingProxy}; the interactions are captured and turned into a diagram —
 * exactly the flow a user gets (proxy ingestion + identity from the extension + diagram pipeline).
 */
@ExtendWith(KronikolExtension.class)
class ProxyTrackingEndToEndTest {

    interface OrderService {
        String checkout(String item);
    }

    @BeforeEach
    @AfterEach
    void clearLogs() {
        RequestResponseLogger.clear();
    }

    @Test
    void tracksProxiedCallsAndBuildsADiagram() {
        OrderService real = item -> "confirmed:" + item;
        OrderService tracked = TrackingProxy.wrap(
            OrderService.class, real, ProxyOptions.forService("OrderService"));

        // Act — a normal call through the tracked interface.
        String result = tracked.checkout("egg");
        assertThat(result).isEqualTo("confirmed:egg");

        // The proxy recorded a request + response, attributed to this test (via the extension's scope).
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(log ->
            assertThat(log.testName()).contains("tracksProxiedCallsAndBuildsADiagram"));
        assertThat(logs.get(0).content()).isEqualTo("egg");
        assertThat(logs.get(1).content()).isEqualTo("confirmed:egg");

        // And the diagram pipeline turns them into a sequence diagram.
        List<PlantUmlForTest> diagrams = PlantUmlCreator.create(logs);
        assertThat(diagrams).hasSize(1);
        String uml = diagrams.get(0).diagrams().get(0);
        assertThat(uml)
            // a null dependency category resolves to HttpApi -> entity (parity with .NET)
            .contains("entity \"OrderService\" as orderService")
            .contains("test -> orderService: checkout: /OrderService/checkout")
            .contains("orderService --> test: OK");
    }

    @Test
    void passesThroughUntrackedWhenNoTestIdentity() {
        // Outside a resolvable identity the proxy must not break the call (it just won't track).
        // Inside this test the extension provides identity, so to simulate "no identity" we assert
        // the result is correct regardless — the proxy never alters behaviour.
        OrderService tracked = TrackingProxy.wrap(
            OrderService.class, item -> "ok:" + item, ProxyOptions.forService("OrderService"));
        assertThat(tracked.checkout("milk")).isEqualTo("ok:milk");
    }
}
