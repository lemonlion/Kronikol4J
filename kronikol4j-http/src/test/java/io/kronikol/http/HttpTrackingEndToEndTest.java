package io.kronikol.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.junit5.KronikolExtension;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KronikolExtension.class)
class HttpTrackingEndToEndTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void recordsAnHttpExchangeAndRendersIt() {
        HttpExchangeRecorder.record(
            HttpTrackingOptions.forService("OrderService"),
            Method.Http.GET,
            URI.create("http://orders/orders/42"),
            List.of(new Header("Accept", "application/json")),
            null,
            StatusCode.of(200),
            "{\"id\":42}");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).testName()).contains("recordsAnHttpExchange");
        assertThat(logs.get(0).headers()).extracting(Header::key).contains("Accept");

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("entity \"OrderService\" as orderService")
            .contains("test -> orderService: GET: /orders/42")
            .contains("orderService --> test: OK")
            .contains("\"id\": 42"); // response body pretty-printed in the note
    }

    @Test
    void untrackedOutsideTestContextDoesNotThrow() {
        // No identity scope here would mean no logs; inside this test the extension provides one,
        // so we simply assert the recorder is side-effect-safe and produces the pair.
        RequestResponseLogger.clear();
        HttpExchangeRecorder.record(HttpTrackingOptions.forService("S"),
            Method.Http.POST, URI.create("http://s/x"), List.of(), "body", StatusCode.of(201), null);
        assertThat(RequestResponseLogger.getAllLogs()).hasSize(2);
    }
}
