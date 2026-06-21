package io.kronikol.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * Byte-for-byte parity of the Java browser-rendered HTML report against the .NET golden fixture
 * ({@code report-simple.html}, captured from the real {@code ReportGenerator.GenerateHtmlReport} via
 * the parity harness). Every byte must match except the {@code puml-data} gzip payload, which is not
 * byte-stable across runtimes (plan §6.4) and is therefore asserted by <em>decoded</em> equality.
 */
class GoldenHtmlParityTest {

    /** The exact version the harness stamped into the golden ({@code Kronikol v…}). */
    private static final String PINNED_VERSION = "3.0.43+de7b45a8cc2e3ef102d1455f3f100f4d8d10a17c";

    private static final String PUML_OPEN = "<script id=\"puml-data\" type=\"application/json\">";
    private static final Pattern PUML_PAIR =
        Pattern.compile("\"([^\"]+)\":\"((?:\\\\u[0-9A-Fa-f]{4}|\\\\.|[^\"\\\\])*)\"");

    @Test
    void browserHtmlReport_isByteForByteIdenticalToDotNetGolden() throws IOException {
        Scenario scenario = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true)
            .durationMs(1500)
            .build();
        Feature feature = new Feature("Checkout", List.of(scenario));
        Map<String, String> diagramByTestId = Map.of(
            "s1", "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml");

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-simple.html", actual);
    }

    @Test
    void richBrowserHtmlReport_componentDiagramAndFailure_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        Scenario passed = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).build();
        Scenario failed = Scenario.builder("Checkout rejects empty cart", "s2", ExecutionStatus.FAILED)
            .durationMs(12)
            .error("Expected <400> but got <500> & failed")
            .errorStackTrace("at Checkout.Validate()\n  at Checkout.Run()")
            .build();
        Feature feature = new Feature("Checkout", List.of(passed, failed));
        String diagram = "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml";
        Map<String, String> diagramByTestId = Map.of("s1", diagram, "s2", diagram);
        String componentDiagram = "@startuml\n[Test] --> [OrderService] : HTTP\n@enduml";

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, componentDiagram, "Kronikol Run", PINNED_VERSION);

        assertParity("report-rich.html", actual);
    }

    @Test
    void stepsBrowserHtmlReport_backgroundAndSteps_isByteForByteIdenticalToDotNetGolden() throws IOException {
        ScenarioStep substep =
            new ScenarioStep(null, "POST /checkout", ExecutionStatus.PASSED, 400L, List.of(), List.of());
        Scenario scenario = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500)
            .backgroundSteps(List.of(
                new ScenarioStep("Given", "a logged-in user", ExecutionStatus.PASSED, 10L, List.of(), List.of())))
            .steps(List.of(
                new ScenarioStep("Given", "an empty cart", ExecutionStatus.PASSED, 20L, List.of(), List.of()),
                new ScenarioStep("When", "the user checks out", ExecutionStatus.PASSED, 500L, List.of(substep), List.of()),
                new ScenarioStep("Then", "the order is confirmed", ExecutionStatus.PASSED, 30L, List.of(), List.of())))
            .build();
        Feature feature = new Feature("Checkout", List.of(scenario));
        Map<String, String> diagramByTestId = Map.of(
            "s1", "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml");

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-steps.html", actual);
    }

    /** Asserts byte-identity (outside the gzip puml-data) and decoded-equality of the puml-data. */
    private static void assertParity(String goldenName, String actual) throws IOException {
        Path dump = Path.of("build", "parity", goldenName.replace(".html", ".actual.html"));
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, actual, StandardCharsets.UTF_8);

        String golden = readGolden(goldenName);
        assertEquals(mask(golden), mask(actual),
            "HTML differs outside the puml-data block — see " + dump.toAbsolutePath());
        assertEquals(decodePumlData(golden), decodePumlData(actual),
            "puml-data decodes differently between golden and actual (" + goldenName + ")");
    }

    private static String readGolden(String name) {
        try (InputStream in = GoldenHtmlParityTest.class.getResourceAsStream("/parity/" + name)) {
            assertTrue(in != null, "golden fixture /parity/" + name + " not found on test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Replaces the puml-data script's JSON body with a placeholder so the rest can be byte-compared. */
    private static String mask(String html) {
        int start = html.indexOf(PUML_OPEN);
        if (start < 0) {
            return html;
        }
        int contentStart = start + PUML_OPEN.length();
        int end = html.indexOf("</script>", contentStart);
        return html.substring(0, contentStart) + "__PUML_DATA__" + html.substring(end);
    }

    /** Extracts the puml-data JSON, base64-decodes + gunzips each value to its raw PlantUML. */
    private static Map<String, String> decodePumlData(String html) {
        int start = html.indexOf(PUML_OPEN);
        if (start < 0) {
            return Map.of();
        }
        int contentStart = start + PUML_OPEN.length();
        int end = html.indexOf("</script>", contentStart);
        String json = html.substring(contentStart, end);
        Map<String, String> decoded = new LinkedHashMap<>();
        Matcher m = PUML_PAIR.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String base64 = jsonUnescape(m.group(2));
            decoded.put(key, gunzip(Base64.getDecoder().decode(base64)));
        }
        return decoded;
    }

    private static String jsonUnescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'u' -> {
                    out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                default -> out.append(n); // \" \\ \/
            }
        }
        return out.toString();
    }

    private static String gunzip(byte[] gz) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
