package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportGeneratorTest {

    @BeforeEach
    @AfterEach
    void clearLogs() {
        RequestResponseLogger.clear();
    }

    private static void trackCheckout() {
        UUID trace = UUID.randomUUID();
        UUID rr = UUID.randomUUID();
        RequestResponseLogger.log(RequestResponseLog.builder()
            .testName("Checkout succeeds").testId("t1")
            .method(Method.Http.POST).uri(URI.create("http://orders/checkout"))
            .serviceName("OrderService").callerName("Test")
            .type(RequestResponseType.REQUEST).traceId(trace).requestResponseId(rr)
            .dependencyCategory(DependencyCategories.HTTP).content("{\"item\":\"egg\"}")
            .build());
        RequestResponseLogger.log(RequestResponseLog.builder()
            .testName("Checkout succeeds").testId("t1")
            .method(Method.Http.POST).uri(URI.create("http://orders/checkout"))
            .serviceName("OrderService").callerName("Test")
            .type(RequestResponseType.RESPONSE).traceId(trace).requestResponseId(rr)
            .statusCode(StatusCode.of(200)).content("{\"ok\":true}")
            .build());
    }

    @Test
    void producesADiagrammedHtmlReportEndToEnd(@TempDir Path dir) throws IOException {
        // Simulate a tracked test run through the real static sink.
        trackCheckout();

        var features = List.of(new Feature("Checkout",
            List.of(Scenario.passed("Checkout succeeds", "t1"))));

        var report = HtmlReportGenerator.generate(features, RequestResponseLogger.getAllLogs(), dir, "Demo Run");

        assertThat(report.htmlFile()).exists();
        String html = Files.readString(report.htmlFile());
        assertThat(html)
            .startsWith("<!DOCTYPE html>")
            .contains("<title>Demo Run</title>")
            .contains("1 scenarios · 1 passed · 0 failed")
            .contains("Checkout succeeds")
            .contains("badge passed")
            .contains("class=\"plantuml-browser\" id=\"puml-0\"")
            .contains("id=\"kronikol-diagrams\"")                        // JSON diagram data map
            .contains("plantuml.js")                                     // PlantUML-WASM from the CDN
            .contains("plantumlLoad")                                    // the bundled browser renderer
            .contains("test -[#438DD5]&gt; orderService: POST: /checkout") // raw PlantUML (coloured), HTML-escaped
            .endsWith("</html>\n");
    }

    @Test
    void honoursColourOptions(@TempDir Path dir) throws IOException {
        trackCheckout();
        var features = List.of(new Feature("Checkout",
            List.of(Scenario.passed("Checkout succeeds", "t1"))));

        var report = HtmlReportGenerator.generate(features, RequestResponseLogger.getAllLogs(), dir,
            "Demo Run", ReportOptions.defaults().withArrowColors(true).withParticipantColors(true));

        String html = Files.readString(report.htmlFile());
        assertThat(html)
            .contains("-[#438DD5]")            // coloured request/response arrows
            .contains("orderService #438DD5"); // coloured participant declaration
    }

    @Test
    void defaultGenerateColoursArrowsButNotParticipants(@TempDir Path dir) throws IOException {
        trackCheckout();
        var features = List.of(new Feature("Checkout",
            List.of(Scenario.passed("Checkout succeeds", "t1"))));

        var report = HtmlReportGenerator.generate(features, RequestResponseLogger.getAllLogs(), dir, "Demo Run");

        // The .NET defaults: arrows coloured per dependency type, participants left uncoloured.
        assertThat(Files.readString(report.htmlFile()))
            .contains("-[#438DD5]")
            .doesNotContain("orderService #438DD5");
    }

    @Test
    void defaultsAssetBaseToTheCdn() {
        String html = HtmlReportGenerator.renderHtml(List.of(), java.util.Map.of(), "Demo");
        assertThat(html)
            .contains("src=\"https://cdn.jsdelivr.net/gh/lemonlion/")
            .contains("/viz-global.js\"")
            .contains("/plantuml.js\"");
    }

    @Test
    void assetBaseOverrideEmitsAnOfflineSelfContainedReport() {
        String previous = System.getProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY);
        System.setProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY, "./assets/"); // trailing slash trimmed
        try {
            String html = HtmlReportGenerator.renderHtml(List.of(), java.util.Map.of(), "Demo");
            assertThat(html)
                .contains("src=\"./assets/viz-global.js\"")
                .contains("src=\"./assets/plantuml.js\"")
                .doesNotContain("cdn.jsdelivr.net");                 // no network dependency
        } finally {
            if (previous == null) {
                System.clearProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY);
            } else {
                System.setProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY, previous);
            }
        }
    }

    @Test
    void rendersFailedScenarioWithError(@TempDir Path dir) throws IOException {
        var features = List.of(new Feature("Checkout", List.of(
            new Scenario("Checkout rejects empty cart", "t2", ExecutionStatus.FAILED, 12,
                "expected <400> but got <500>"))));

        var report = HtmlReportGenerator.generate(features, List.of(), dir, "Demo Run");
        String html = Files.readString(report.htmlFile());

        assertThat(html)
            .contains("badge failed")
            .contains("12 ms")
            .contains("expected &lt;400&gt; but got &lt;500&gt;")  // error, escaped
            .contains("0 passed · 1 failed");
    }
}
