package io.kronikol.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.report.ReportOptions;
import io.kronikol.report.data.ReportDataFormat;
import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportFinalizerTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RunResults.clear();
        RequestResponseLogger.clear();
    }

    @Test
    void recordsScenariosGroupedByFeatureInOrder() {
        RunResults.record("Checkout", Scenario.passed("a", "1"));
        RunResults.record("Payments", Scenario.passed("b", "2"));
        RunResults.record("Checkout", Scenario.passed("c", "3"));

        List<Feature> features = RunResults.toFeatures();
        assertThat(features).extracting(Feature::displayName).containsExactly("Checkout", "Payments");
        assertThat(features.get(0).scenarios()).extracting(Scenario::name).containsExactly("a", "c");
    }

    @Test
    void finalizeReturnsNullWhenNothingTracked() throws IOException {
        assertThat(ReportFinalizer.finalizeRun(Path.of("unused"), "x")).isNull();
    }

    @Test
    void finalizeWritesReportFromCollectedResults(@TempDir Path dir) throws IOException {
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        var report = ReportFinalizer.finalizeRun(dir, "Run");
        assertThat(report).isNotNull();
        assertThat(Files.readString(report.htmlFile()))
            .contains("Checkout succeeds")
            .contains("data-status=\"Passed\""); // .NET-parity scenario marker
    }

    @Test
    void forkedModeDetectedFromRunDirProperty() {
        assertThat(ReportFinalizer.isForkedMode()).isFalse();
        System.setProperty(ReportFinalizer.RUN_DIR_PROPERTY, "/tmp/run-123");
        try {
            assertThat(ReportFinalizer.isForkedMode()).isTrue();
        } finally {
            System.clearProperty(ReportFinalizer.RUN_DIR_PROPERTY);
        }
    }

    @Test
    void writesAnAtomicFragmentFromCollectedResults(@TempDir Path dir) throws IOException {
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        Path file = ReportFinalizer.writeFragment(dir, "fragment-test.json", "Run");

        assertThat(file).exists();
        assertThat(dir.resolve("fragment-test.json.tmp")).doesNotExist(); // temp cleaned up
        var fragment = FragmentJson.fromJson(Files.readString(file));
        assertThat(fragment.features()).anySatisfy(f ->
            assertThat(f.scenarios()).extracting(Scenario::name).contains("Checkout succeeds"));
    }

    @Test
    void writeFragmentReturnsNullWhenNothingTracked(@TempDir Path dir) throws IOException {
        assertThat(ReportFinalizer.writeFragment(dir, "f.json", "Run")).isNull();
    }

    @Test
    void finalizeThreadsColourOptionsIntoTheDiagram(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        var report = ReportFinalizer.finalizeRun(dir, "Run",
            ReportOptions.defaults().withArrowColors(true).withParticipantColors(true));

        // The diagram is gzip-compressed in the puml-data island (.NET parity); decode to check colours.
        assertThat(decodedDiagrams(Files.readString(report.htmlFile())))
            .contains("-[#438DD5]")            // coloured arrow
            .contains("orderService #438DD5"); // coloured participant
    }

    @Test
    void finalizeRunToDefaultReadsColourSystemProperties(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));
        System.setProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY, dir.toString());
        System.setProperty(ReportOptions.ARROW_COLORS_PROPERTY, "true");
        try {
            var report = ReportFinalizer.finalizeRunToDefault("Run");
            assertThat(decodedDiagrams(Files.readString(report.htmlFile()))).contains("-[#438DD5]");
        } finally {
            System.clearProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY);
            System.clearProperty(ReportOptions.ARROW_COLORS_PROPERTY);
        }
    }

    @Test
    void finalizeEmitsRequestedDataFiles(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run",
            ReportOptions.defaults().withDataFormats(Set.of(ReportDataFormat.XML, ReportDataFormat.YAML)));

        assertThat(dir.resolve("TestRunReport.html")).exists();
        assertThat(dir.resolve("TestRunReport.json")).doesNotExist(); // not requested
        assertThat(Files.readString(dir.resolve("TestRunReport.xml")))
            .startsWith("<TestRunReport>")
            .contains("<Name>Checkout succeeds</Name>")
            .contains("<HttpInteraction>")        // the tracked interaction
            .contains("<Diagram>");                // the per-test diagram
        assertThat(Files.readString(dir.resolve("TestRunReport.yaml")))
            .startsWith("KronikolVersion:")
            .contains("- Name: Checkout succeeds")
            .contains("HttpInteractions:");
    }

    @Test
    void finalizeRunToDefaultReadsDataFormatsSystemProperty(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));
        System.setProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY, dir.toString());
        System.setProperty(ReportOptions.DATA_FORMATS_PROPERTY, "yaml");
        try {
            ReportFinalizer.finalizeRunToDefault("Run");
            assertThat(dir.resolve("TestRunReport.yaml")).exists();
            assertThat(dir.resolve("TestRunReport.xml")).doesNotExist();
        } finally {
            System.clearProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY);
            System.clearProperty(ReportOptions.DATA_FORMATS_PROPERTY);
        }
    }

    @Test
    void finalizeEmitsSchemaWhenRequested(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run", ReportOptions.defaults()
            .withDataFormats(Set.of(ReportDataFormat.JSON, ReportDataFormat.XML)).withGenerateSchema(true));

        assertThat(Files.readString(dir.resolve("TestRunReport.schema.json")))
            .contains("\"title\": \"TestRunReport\"").contains("https://json-schema.org/draft/2020-12/schema");
        assertThat(Files.readString(dir.resolve("TestRunReport.schema.xsd")))
            .startsWith("<xs:schema").contains("name=\"ExecutionResult\"");
        // not emitted without the flag
        ReportFinalizer.finalizeRun(dir.resolve("noschema"), "Run",
            ReportOptions.defaults().withDataFormats(Set.of(ReportDataFormat.JSON)));
        assertThat(dir.resolve("noschema/TestRunReport.schema.json")).doesNotExist();
    }

    @Test
    void finalizeWritesCiSummaryWhenEnabled(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run", ReportOptions.defaults()
            .withCi(new io.kronikol.report.ci.CiPublishOptions(true, 10, false, "TestReports", 1)));

        // The CI summary markdown is written to disk regardless of the detected CI platform.
        assertThat(Files.readString(dir.resolve("CiSummary.md")))
            .startsWith("# Diagrammed Test Run Summary")
            .contains("| Passed | 1 |")
            .contains("```plantuml"); // the run's diagram embedded
    }

    @Test
    void finalizeDoesNotWriteCiSummaryByDefault(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run");

        assertThat(dir.resolve("CiSummary.md")).doesNotExist();
    }

    @Test
    void finalizeRunToDefaultReadsCiSystemProperties(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));
        System.setProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY, dir.toString());
        System.setProperty(ReportOptions.WRITE_CI_SUMMARY_PROPERTY, "true");
        try {
            ReportFinalizer.finalizeRunToDefault("Run");
            assertThat(dir.resolve("CiSummary.md")).exists();
        } finally {
            System.clearProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY);
            System.clearProperty(ReportOptions.WRITE_CI_SUMMARY_PROPERTY);
        }
    }

    @Test
    void finalizeUsesConfiguredHtmlFileName(@TempDir Path dir) throws IOException {
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        var report = ReportFinalizer.finalizeRun(dir, "Run",
            ReportOptions.defaults().withHtmlReportFileName("MyReport"));

        assertThat(dir.resolve("MyReport.html")).exists();
        assertThat(dir.resolve("TestRunReport.html")).doesNotExist();
        assertThat(report.htmlFile().getFileName().toString()).isEqualTo("MyReport.html");
    }

    @Test
    void finalizeEmitsTestRunReportJsonByDefault(@TempDir Path dir) throws IOException {
        // .NET parity: GenerateTestRunReportData defaults true + TestRunReportDataFormat defaults JSON,
        // so a default run emits TestRunReport.json (no explicit dataFormats needed).
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run");

        assertThat(Files.readString(dir.resolve("TestRunReport.json")))
            .contains("Checkout succeeds");
        assertThat(dir.resolve("TestRunReport.xml")).doesNotExist(); // only the default JSON
    }

    @Test
    void finalizeSkipsDataWhenGenerateTestRunReportDataDisabled(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        // Kill switch: no data file even though a format is explicitly requested.
        ReportFinalizer.finalizeRun(dir, "Run", ReportOptions.defaults()
            .withDataFormats(Set.of(ReportDataFormat.JSON)).withGenerateTestRunReportData(false));

        assertThat(dir.resolve("TestRunReport.json")).doesNotExist();
        assertThat(dir.resolve("TestRunReport.html")).exists(); // HTML still written
    }

    @Test
    void finalizeWritesMergeableFragmentWhenEnabled(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run", ReportOptions.defaults().withGenerateMergeableData(true));

        Path fragment = dir.resolve("TestRunReport.mergeable.json");
        assertThat(fragment).exists();
        var parsed = FragmentJson.fromJson(Files.readString(fragment)); // round-trips as a real fragment
        assertThat(parsed.features()).anySatisfy(f ->
            assertThat(f.scenarios()).extracting(Scenario::name).contains("Checkout succeeds"));
    }

    @Test
    void finalizeDoesNotWriteMergeableFragmentByDefault(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run");

        assertThat(dir.resolve("TestRunReport.mergeable.json")).doesNotExist();
    }

    @Test
    void finalizeOmitsComponentDiagramWhenDisabled(@TempDir Path dir) throws IOException {
        trackCheckout(); // a Test -> OrderService HTTP interaction → a run-level component diagram
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        var on = ReportFinalizer.finalizeRun(dir.resolve("on"), "Run", ReportOptions.defaults());
        assertThat(Files.readString(on.htmlFile())).contains("id=\"component-diagram\""); // default: present

        var off = ReportFinalizer.finalizeRun(dir.resolve("off"), "Run",
            ReportOptions.defaults().withGenerateComponentDiagram(false));
        assertThat(Files.readString(off.htmlFile())).doesNotContain("id=\"component-diagram\"");
    }

    @Test
    void finalizeAppliesTitleOverride(@TempDir Path dir) throws IOException {
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        var report = ReportFinalizer.finalizeRun(dir, "Caller Title",
            ReportOptions.defaults().withTestRunReportTitle("Configured Title"));

        assertThat(Files.readString(report.htmlFile()))
            .contains("<title>Configured Title</title>")
            .doesNotContain("<title>Caller Title</title>");
    }

    @Test
    void finalizeRunToDefaultReadsTitleAndFileNameSystemProperties(@TempDir Path dir) throws IOException {
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));
        System.setProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY, dir.toString());
        System.setProperty(ReportOptions.REPORT_TITLE_PROPERTY, "Sys Title");
        System.setProperty(ReportOptions.HTML_FILE_NAME_PROPERTY, "SysReport");
        try {
            ReportFinalizer.finalizeRunToDefault("Kronikol4J Test Run");
            assertThat(Files.readString(dir.resolve("SysReport.html"))).contains("<title>Sys Title</title>");
        } finally {
            System.clearProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY);
            System.clearProperty(ReportOptions.REPORT_TITLE_PROPERTY);
            System.clearProperty(ReportOptions.HTML_FILE_NAME_PROPERTY);
        }
    }

    @Test
    void finalizeWritesDiagnosticReportWhenEnabled(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run", ReportOptions.defaults().withDiagnosticMode(true));

        assertThat(dir.resolve("TestRunReport.html")).exists();
        assertThat(Files.readString(dir.resolve("DiagnosticReport.html")))
            .contains("Kronikol — Diagnostic Report")
            .contains("Request/Response Log Summary");
    }

    @Test
    void finalizeWritesDiagnosticReportWhenLogsButNoTestContexts(@TempDir Path dir) throws IOException {
        // Logs were recorded but no scenarios enqueued — the main report is skipped, but the diagnostic
        // report is still written to explain the empty result (matches .NET's empty-path branch).
        trackCheckout(); // logs exist...
        // ...but no RunResults.record(...) call → RunResults.isEmpty()

        var report = ReportFinalizer.finalizeRun(dir, "Run", ReportOptions.defaults().withDiagnosticMode(true));

        assertThat(report).isNull(); // empty run → no main report
        assertThat(dir.resolve("TestRunReport.html")).doesNotExist();
        assertThat(Files.readString(dir.resolve("DiagnosticReport.html")))
            .contains("Kronikol — Diagnostic Report")
            .contains("2 total log entries"); // the tracked request + response pair
    }

    @Test
    void finalizeDoesNotWriteDiagnosticReportByDefault(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        ReportFinalizer.finalizeRun(dir, "Run");

        assertThat(dir.resolve("DiagnosticReport.html")).doesNotExist();
    }

    @Test
    void finalizeRunToDefaultReadsDiagnosticModeSystemProperty(@TempDir Path dir) throws IOException {
        trackCheckout();
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));
        System.setProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY, dir.toString());
        System.setProperty(ReportOptions.DIAGNOSTIC_MODE_PROPERTY, "true");
        try {
            ReportFinalizer.finalizeRunToDefault("Run");
            assertThat(dir.resolve("DiagnosticReport.html")).exists();
        } finally {
            System.clearProperty(ReportFinalizer.OUTPUT_DIR_PROPERTY);
            System.clearProperty(ReportOptions.DIAGNOSTIC_MODE_PROPERTY);
        }
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

    /** Extracts + decodes the gzip+base64 PlantUML island the .NET-parity report stores (plan §6.4),
     *  so colour/diagram-content assertions can inspect the source rather than the inline bytes. */
    private static String decodedDiagrams(String html) {
        String open = "<script id=\"puml-data\" type=\"application/json\">";
        int start = html.indexOf(open);
        if (start < 0) {
            return "";
        }
        int contentStart = start + open.length();
        String json = html.substring(contentStart, html.indexOf("</script>", contentStart));
        StringBuilder all = new StringBuilder();
        Matcher m = Pattern.compile("\"[^\"]+\":\"((?:\\\\u[0-9A-Fa-f]{4}|\\\\.|[^\"\\\\])*)\"").matcher(json);
        while (m.find()) {
            byte[] gz = Base64.getDecoder().decode(jsonUnescape(m.group(1)));
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
                all.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return all.toString();
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
            if (n == 'u') {
                out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                out.append(switch (n) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> n;
                });
            }
        }
        return out.toString();
    }
}
