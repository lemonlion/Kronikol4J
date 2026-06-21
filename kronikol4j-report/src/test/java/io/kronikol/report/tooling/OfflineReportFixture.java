package io.kronikol.report.tooling;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Generates a self-contained, offline-renderable Kronikol4J report for the Playwright pixel test
 * (plan §3.5 / §6.5 visual verification). Writes {@code report.html} plus the two PlantUML-WASM
 * assets ({@code viz-global.js}, {@code plantuml.js}) into one directory and points the report's
 * asset base at {@code "."} so it renders with no network access — real library, real browser, no
 * mocking. The assets are taken from the local cache the .NET renderer populates
 * ({@code %LOCALAPPDATA%/Kronikol/plantuml-js}); if absent they are downloaded from the CDN once.
 *
 * <p>Usage: {@code OfflineReportFixture <output-dir>} (run via the {@code generatePlaywrightFixture}
 * Gradle task).
 */
public final class OfflineReportFixture {

    private static final String CDN =
        "https://cdn.jsdelivr.net/gh/lemonlion/plantuml-js-plantuml_limit_size_98304@v1.2026.3beta6-patched";
    private static final String[] ASSETS = {"viz-global.js", "plantuml.js"};

    private OfflineReportFixture() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Path outDir = Path.of(args.length > 0 ? args[0] : "build/playwright").toAbsolutePath();
        Files.createDirectories(outDir);

        copyAssets(outDir);

        // Render the report with the assets sitting next to it (asset base ".").
        String previous = System.getProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY);
        System.setProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY, ".");
        try {
            RequestResponseLogger.clear();
            trackSampleInteraction();
            List<Feature> features = List.of(new Feature("Checkout",
                List.of(Scenario.passed("Checkout succeeds", "t1"))));
            var report = HtmlReportGenerator.generate(
                features, RequestResponseLogger.getAllLogs(), outDir, "Kronikol4J Render Check");
            // Normalise the filename the Playwright spec loads.
            Path target = outDir.resolve("report.html");
            Files.move(report.htmlFile(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Wrote offline report: " + target);
        } finally {
            if (previous == null) {
                System.clearProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY);
            } else {
                System.setProperty(HtmlReportGenerator.ASSET_BASE_PROPERTY, previous);
            }
            RequestResponseLogger.clear();
        }
    }

    private static void trackSampleInteraction() {
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

    private static void copyAssets(Path outDir) throws IOException, InterruptedException {
        Path cache = localCacheDir();
        for (String asset : ASSETS) {
            Path dest = outDir.resolve(asset);
            Path cached = cache == null ? null : cache.resolve(asset);
            if (cached != null && Files.isRegularFile(cached)) {
                Files.copy(cached, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Copied " + asset + " from cache (" + Files.size(dest) + " bytes)");
            } else {
                download(CDN + "/" + asset, dest);
                System.out.println("Downloaded " + asset + " from CDN (" + Files.size(dest) + " bytes)");
            }
        }
    }

    private static Path localCacheDir() {
        String override = System.getProperty("kronikol.plantuml.libDir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "Kronikol", "plantuml-js");
        }
        return null;
    }

    private static void download(String url, Path dest) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<byte[]> response = client.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download " + url + " (HTTP " + response.statusCode() + ")");
        }
        Files.write(dest, response.body());
    }
}
