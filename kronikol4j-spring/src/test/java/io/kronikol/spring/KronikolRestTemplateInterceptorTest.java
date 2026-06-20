package io.kronikol.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.http.HttpTrackingOptions;
import io.kronikol.junit5.KronikolExtension;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

@ExtendWith(KronikolExtension.class)
class KronikolRestTemplateInterceptorTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    @Test
    void recordsTheExchangeAndPreservesTheResponseBody() throws Exception {
        var interceptor = new KronikolRestTemplateInterceptor(HttpTrackingOptions.forService("OrderService"));

        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
            URI.create("http://orders/orders/42"));
        request.getHeaders().add("Accept", "application/json");

        ClientHttpRequestExecution execution = (req, body) ->
            new MockClientHttpResponse("{\"id\":42}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);

        ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);

        // The caller still receives the full response body (buffered, not consumed by tracking).
        assertThat(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":42}");
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).method().value()).isEqualTo("GET");
        assertThat(logs.get(0).headers()).extracting(Header::key).contains("Accept");
        assertThat(logs.get(1).content()).isEqualTo("{\"id\":42}"); // raw response body captured

        String uml = PlantUmlCreator.create(logs).get(0).diagrams().get(0);
        assertThat(uml)
            .contains("Test -> OrderService : GET /orders/42")
            .contains("OrderService --> Test : OK")
            .contains("\"id\": 42"); // pretty-printed in the diagram note
    }
}
