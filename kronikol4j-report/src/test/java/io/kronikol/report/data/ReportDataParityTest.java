package io.kronikol.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Byte-for-byte cross-runtime parity for the test-run report data: the Java {@link
 * ReportDataSerializer} must match the real .NET {@code GenerateTestRunReportData} JSON / XML / YAML
 * over an identical rich corpus (the {@code report-data.*} fixtures captured by
 * {@code parity-harness/dotnet-capture}). Only the trailing newline is normalised.
 */
class ReportDataParityTest {

    // The .NET assembly's informational version stamped into the captured fixtures (re-capture if it changes).
    private static final String VERSION = "3.0.43+bd0b671c8c4f5741b97ec6a1c25f4f7dc99fdbb9";

    @Test
    void json() throws IOException {
        assertThat(normalize(ReportDataSerializer.toJson(corpus())))
            .isEqualTo(normalize(fixture("report-data.json")));
    }

    @Test
    void xml() throws IOException {
        assertThat(normalize(ReportDataSerializer.toXml(corpus())))
            .isEqualTo(normalize(fixture("report-data.xml")));
    }

    @Test
    void yaml() throws IOException {
        assertThat(normalize(ReportDataSerializer.toYaml(corpus())))
            .isEqualTo(normalize(fixture("report-data.yaml")));
    }

    /** Built identically to {@code parity-harness/dotnet-capture/Program.cs} {@code CaptureReportData}. */
    private static ReportData corpus() {
        var bgStep = new ScenarioStep("Given", "a logged-in user", ExecutionStatus.PASSED, 10L, List.of(), List.of());
        var step1 = new ScenarioStep("Given", "an empty cart", ExecutionStatus.PASSED, 20L, List.of(), List.of());
        var substep = new ScenarioStep(null, "POST /checkout", ExecutionStatus.PASSED, 400L, List.of(), List.of());
        var step2 = new ScenarioStep("When", "the user checks out & pays", ExecutionStatus.PASSED, 500L,
            List.of(substep), List.of());
        var step3 = new ScenarioStep("Then", "the order is confirmed", ExecutionStatus.PASSED, 30L, List.of(),
            List.of(new FileAttachment("receipt.pdf", "attachments/receipt.pdf")));

        var s1 = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500)
            .labels(List.of("fast")).categories(List.of("api")).rule("Happy path")
            .backgroundSteps(List.of(bgStep)).steps(List.of(step1, step2, step3))
            .attachments(List.of(new FileAttachment("trace.log", "attachments/trace.log")))
            .build();

        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("cart", "empty");
        examples.put("expected", "400");
        var s2 = Scenario.builder("Checkout rejects empty cart", "s2", ExecutionStatus.FAILED)
            .isHappyPath(false).durationMs(12)
            .error("Expected <400> but got <500> & failed")
            .errorStackTrace("at Checkout.Validate()\n  at Checkout.Run()")
            .outlineId("outline-1").exampleValues(examples).exampleDisplayName("Empty cart")
            .build();

        var feature = new Feature("Checkout", List.of(s1, s2),
            "/checkout", "The checkout flow", List.of("smoke", "critical"));

        UUID trace = UUID.fromString("00000000-0000-0000-0000-000000000007");
        UUID rr = UUID.fromString("00000000-0000-0000-0000-000000000107");
        var request = RequestResponseLog.builder()
            .testName("Checkout succeeds").testId("s1").method(Method.Http.POST)
            .uri(URI.create("http://orders/checkout")).serviceName("OrderService").callerName("Test")
            .type(RequestResponseType.REQUEST).traceId(trace).requestResponseId(rr).dependencyCategory("HTTP")
            .content("{\"item\":\"egg\"}").headers(List.of(new Header("Accept", "application/json")))
            .timestamp(OffsetDateTime.parse("2024-01-15T10:00:01.234Z"))
            .build();
        var response = RequestResponseLog.builder()
            .testName("Checkout succeeds").testId("s1").method(Method.Http.POST)
            .uri(URI.create("http://orders/checkout")).serviceName("OrderService").callerName("Test")
            .type(RequestResponseType.RESPONSE).traceId(trace).requestResponseId(rr).dependencyCategory("HTTP")
            .statusCode(StatusCode.of(200)).content("{\"ok\":true}")
            .timestamp(OffsetDateTime.parse("2024-01-15T10:00:01.250Z"))
            .build();

        Map<String, List<String>> diagrams = Map.of("s1",
            List.of("@startuml\nactor Test\nTest -> OrderService\n@enduml"));
        Map<String, List<RequestResponseLog>> logs = Map.of("s1", List.of(request, response));

        return new ReportData(VERSION, Instant.parse("2024-01-15T10:00:00Z"),
            Instant.parse("2024-01-15T10:00:05Z"), List.of(feature), diagrams, logs);
    }

    private static String normalize(String text) {
        String unix = text.replace("\r\n", "\n").replace("\r", "\n");
        int end = unix.length();
        while (end > 0 && unix.charAt(end - 1) == '\n') {
            end--;
        }
        return unix.substring(0, end);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = ReportDataParityTest.class.getClassLoader().getResourceAsStream("parity/" + name)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found: parity/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
