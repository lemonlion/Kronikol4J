package com.example.componenttests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.kronikol.http.HttpTrackingConfig;
import io.kronikol.http.TrackingHttpClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * A sample component test. Point the {@link TrackingHttpClient} at your system-under-test (or a local fake)
 * instead of the placeholder URL — every request/response made through it becomes a tracked interaction in
 * the generated sequence diagram and report.
 */
class SampleComponentTest extends BaseComponentTest {

    @Test
    void callsDownstreamService() throws Exception {
        // Wrap any java.net.http.HttpClient. callerName is this service's name in the diagram; the receiving
        // service is inferred from the host/port (override with portsToServiceNames / fixedServiceName).
        HttpClient http = new TrackingHttpClient(
            HttpClient.newHttpClient(),
            HttpTrackingConfig.builder().callerName("MyApi").build());

        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:15050/api/health")) // ← replace with your SUT endpoint
            .GET()
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }
}
