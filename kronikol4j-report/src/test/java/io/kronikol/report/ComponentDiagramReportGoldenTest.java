package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.report.component.ComponentDiagramOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * Byte-for-byte parity for the standalone component-diagram HTML report (the .NET
 * {@code ComponentDiagramReportGenerator.GenerateComponentDiagramReport}, browserJs mode). The {@code .html}
 * fixture was captured from the real .NET ({@code parity-harness/dotnet-capture}). The gzip {@code data-plantuml-z}
 * payload is not byte-stable across runtimes (plan §6.4), so it is asserted <em>decoded</em>; everything else
 * (template, favicon, context-menu styles/scripts) is asserted byte-identical.
 */
class ComponentDiagramReportGoldenTest {

    private static final Pattern PUMLZ = Pattern.compile("data-plantuml-z=\"([^\"]*)\"");

    @Test
    void standaloneComponentDiagramReportIsByteIdenticalToDotNet() throws IOException {
        String golden = readResource("/parity/component-diagram-report.html");
        String actual = ComponentDiagramReportGenerator.generateHtml(fanOutCorpus(), ComponentDiagramOptions.defaults());

        // Everything except the (non-byte-stable) gzip payload must be byte-identical.
        assertThat(maskPumlZ(actual)).isEqualTo(maskPumlZ(golden));

        // The gzip payloads must decode to identical PlantUML. The .NET capture embedded CRLF (its
        // ComponentDiagramGenerator uses AppendLine → Environment.NewLine; on the Windows capture host that is
        // \r\n, and the harness only normalises the OUTER html, not the gzip blob). Java emits \n always
        // (repo §6.5), which is the canonical form (a Linux .NET run would embed \n too) — normalise to compare.
        assertThat(normalize(gunzip(extractPumlZ(actual)))).isEqualTo(normalize(gunzip(extractPumlZ(golden))));
    }

    private static String normalize(String s) {
        // CRLF→LF (Windows .NET-capture artifact, §6.5) + trailing-newline normalisation — the same two
        // normalisations the sibling component.puml golden applies (".NET AppendLine adds a trailing newline").
        return s.replace("\r\n", "\n").stripTrailing();
    }

    private static String maskPumlZ(String html) {
        return PUMLZ.matcher(html).replaceAll("data-plantuml-z=\"__Z__\"");
    }

    private static String extractPumlZ(String html) {
        Matcher m = PUMLZ.matcher(html);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static String gunzip(String base64) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = ComponentDiagramReportGoldenTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The .NET {@code FanOut()} corpus (same logs the {@code component.puml} golden uses). */
    private static List<RequestResponseLog> fanOutCorpus() {
        return List.of(
            log(Method.Http.POST, "http://orders/checkout", "OrderService", DependencyCategories.HTTP,
                RequestResponseType.REQUEST, "{\"item\":\"egg\"}", null),
            log(Method.Http.POST, "http://orders/checkout", "OrderService", DependencyCategories.HTTP,
                RequestResponseType.RESPONSE, "{\"ok\":true}", StatusCode.of(200)),
            log(Method.of("SELECT"), "sql://database/", "OrderDb", DependencyCategories.SQL,
                RequestResponseType.REQUEST, "SELECT * FROM orders WHERE id = 1", null),
            log(Method.of("SELECT"), "sql://database/", "OrderDb", DependencyCategories.SQL,
                RequestResponseType.RESPONSE, "1 row", StatusCode.of("OK")),
            log(Method.of("GET"), "redis://cache/", "CartCache", DependencyCategories.REDIS,
                RequestResponseType.REQUEST, "cart:42", null),
            log(Method.of("GET"), "redis://cache/", "CartCache", DependencyCategories.REDIS,
                RequestResponseType.RESPONSE, "{\"items\":2}", StatusCode.of("OK")));
    }

    private static RequestResponseLog log(Method method, String uri, String service, String category,
                                          RequestResponseType type, String content, StatusCode status) {
        return RequestResponseLog.builder()
            .testName("Places an order").testId("t1").method(method).uri(URI.create(uri))
            .serviceName(service).callerName("Test").type(type)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .dependencyCategory(category).statusCode(status).content(content)
            .build();
    }
}
