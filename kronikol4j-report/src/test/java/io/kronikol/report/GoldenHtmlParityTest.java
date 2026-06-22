package io.kronikol.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kronikol.report.flow.InternalFlowDiagramStyle;
import io.kronikol.report.flow.InternalFlowFlameChartPosition;
import io.kronikol.report.flow.InternalFlowHasDataBehavior;
import io.kronikol.report.flow.InternalFlowNoDataBehavior;
import io.kronikol.report.flow.InternalFlowPopupInput;
import io.kronikol.report.flow.InternalFlowSegment;
import io.kronikol.report.flow.InternalFlowSpan;
import io.kronikol.report.flow.InternalFlowSpanGranularity;
import io.kronikol.report.flow.WholeTestFlowInput;
import io.kronikol.report.flow.WholeTestFlowVisualization;
import io.kronikol.report.model.CiEnvironment;
import io.kronikol.report.model.CiMetadata;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.HtmlCustomization;
import io.kronikol.report.model.ParameterizedOptions;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.InlineParameterValue;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import io.kronikol.report.model.StepParameter;
import io.kronikol.report.model.StepTextSegment;
import io.kronikol.report.model.TableRowType;
import io.kronikol.report.model.TabularParameterValue;
import io.kronikol.report.model.TabularParameterValue.TabularCell;
import io.kronikol.report.model.TabularParameterValue.TabularColumn;
import io.kronikol.report.model.TabularParameterValue.TabularRow;
import io.kronikol.report.model.TreeParameterValue;
import io.kronikol.report.model.TreeParameterValue.TreeNode;
import io.kronikol.report.model.VerificationStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
    /** Inline gzipped flame-chart payload — like puml-data, not byte-stable across runtimes (plan §6.4). */
    private static final Pattern FLAME_Z = Pattern.compile("data-flame-z=\"([^\"]*)\"");

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

    @Test
    void statusesBrowserHtmlReport_skippedAndBypassed_isByteForByteIdenticalToDotNetGolden() throws IOException {
        Scenario passed = Scenario.builder("Checkout passes", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).build();
        Scenario skipped = Scenario.builder("Checkout is skipped", "s2", ExecutionStatus.SKIPPED)
            .durationMs(800).build();
        Scenario bypassed = Scenario.builder("Checkout is bypassed", "s3", ExecutionStatus.INCONCLUSIVE)
            .durationMs(600).build();
        Feature feature = new Feature("Checkout", List.of(passed, skipped, bypassed));
        Map<String, String> diagramByTestId = Map.of(
            "s1", "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml");

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-statuses.html", actual);
    }

    @Test
    void attachmentsBrowserHtmlReport_scenarioAndStepLevel_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        ScenarioStep step = new ScenarioStep("Then", "the order is confirmed", ExecutionStatus.PASSED, 30L,
            List.of(),
            List.of(new FileAttachment("step-shot.png", "attachments/step-shot.png"),
                new FileAttachment("log.txt", "attachments/log.txt")));
        Scenario scenario = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500)
            .steps(List.of(step))
            .attachments(List.of(
                new FileAttachment("receipt.pdf", "attachments/receipt.pdf"),
                new FileAttachment("screenshot.png", "attachments/screenshot.png")))
            .build();
        Feature feature = new Feature("Checkout", List.of(scenario));
        Map<String, String> diagramByTestId = Map.of(
            "s1", "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml");

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-attachments.html", actual);
    }

    @Test
    void rulesBrowserHtmlReport_ruleGrouping_isByteForByteIdenticalToDotNetGolden() throws IOException {
        Scenario happy = Scenario.builder("Happy checkout", "s0", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).build();
        Scenario a = Scenario.builder("Adds item", "s1", ExecutionStatus.PASSED)
            .durationMs(100).rule("Cart rules").build();
        Scenario b = Scenario.builder("Browses catalog", "s2", ExecutionStatus.PASSED)
            .durationMs(200).rule("Cart rules").build();
        Scenario c = Scenario.builder("Checks out", "s3", ExecutionStatus.PASSED)
            .durationMs(300).rule("Checkout rules").build();
        Feature feature = new Feature("Shopping", List.of(happy, a, b, c));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-rules.html", actual);
    }

    @Test
    void errorDiffBrowserHtmlReport_expectedActualDiff_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        Scenario failed = Scenario.builder("Checkout validates total", "s1", ExecutionStatus.FAILED)
            .durationMs(15)
            .error("Expected: 400\nActual: 500")
            .errorStackTrace("at Checkout.Validate()")
            .build();
        Feature feature = new Feature("Checkout", List.of(failed));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-errordiff.html", actual);
    }

    @Test
    void escapingBrowserHtmlReport_specialCharsInEveryTextField_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        // Every user-text field carries the markup-trigger marker <b>&"' (which WebUtility renders
        // &lt;b&gt;&amp;&quot;&#39;) so any call site that emits raw / over-escapes diverges byte-for-byte.
        String m = "<b>&\"'";
        ScenarioStep substep =
            new ScenarioStep(null, "substep " + m, ExecutionStatus.PASSED, 5L, List.of(), List.of());
        ScenarioStep step = new ScenarioStep("Given " + m, "a step " + m, ExecutionStatus.PASSED, 20L,
            List.of(substep), List.of(new FileAttachment("file " + m, "attachments/x.png")));
        Scenario passed = Scenario.builder("Adds " + m, "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).rule("Rule " + m)
            .steps(List.of(step))
            .attachments(List.of(new FileAttachment("att " + m, "attachments/y.pdf")))
            .build();
        Scenario failed = Scenario.builder("Rejects " + m, "s2", ExecutionStatus.FAILED)
            .durationMs(12).error("Error " + m).errorStackTrace("at " + m).build();
        Feature feature = new Feature("Feature " + m, List.of(passed, failed));
        String diagram = "@startuml\nactor Test\nTest -> X : POST\n@enduml";
        Map<String, String> diagramByTestId = Map.of("s1", diagram, "s2", diagram);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Title " + m, PINNED_VERSION);

        assertParity("report-escaping.html", actual);
    }

    @Test
    void escapingBrowserHtmlReport_specialCharsInParameters_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        // Parameter names + values (and the OutlineId) carry the marker — covers the ParameterValueRenderer
        // + the scalar-column table call sites.
        String m = "<b>&\"'";
        LinkedHashMap<String, String> ev1 = new LinkedHashMap<>();
        ev1.put("in " + m, "v1 " + m);
        ev1.put("out", "r1");
        LinkedHashMap<String, String> ev2 = new LinkedHashMap<>();
        ev2.put("in " + m, "v2 " + m);
        ev2.put("out", "r2");
        Scenario s1 = Scenario.builder("Case A", "s1", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Outline " + m).exampleValues(ev1).build();
        Scenario s2 = Scenario.builder("Case B", "s2", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Outline " + m).exampleValues(ev2).build();
        Feature feature = new Feature("Math", List.of(s1, s2));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-escaping-params.html", actual);
    }

    @Test
    void edgeFieldsBrowserHtmlReport_nullDurationAndStatus_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        // The optional-field branches the timed goldens never hit: a scenario with no duration (durationMs
        // 0 == "no duration" → no badge/attr, matching .NET Duration.HasValue == false) and a structural
        // step with no status + no duration alongside a normal timed step.
        ScenarioStep structural =
            new ScenarioStep("Given", "a structural step", null, null, List.of(), List.of());
        ScenarioStep timed =
            new ScenarioStep("When", "a timed step", ExecutionStatus.PASSED, 40L, List.of(), List.of());
        Scenario skipped = Scenario.builder("Skipped no duration", "s1", ExecutionStatus.SKIPPED)
            .steps(List.of(structural, timed))
            .build();
        Feature feature = new Feature("Edges", List.of(skipped));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-edgefields.html", actual);
    }

    @Test
    void paramEdgeValuesBrowserHtmlReport_nullAndWhitespace_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        // FormatDisplayValue branches: a literal "null" → <pre>null</pre>, a whitespace-only value →
        // <pre>{ws}</pre>, vs a normal value (escaped inline).
        LinkedHashMap<String, String> ev1 = new LinkedHashMap<>();
        ev1.put("nullish", "null");
        ev1.put("blank", "   ");
        ev1.put("normal", "42");
        LinkedHashMap<String, String> ev2 = new LinkedHashMap<>();
        ev2.put("nullish", "x");
        ev2.put("blank", "y");
        ev2.put("normal", "99");
        Scenario s1 = Scenario.builder("Edge A", "s1", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Edges").exampleValues(ev1).build();
        Scenario s2 = Scenario.builder("Edge B", "s2", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Edges").exampleValues(ev2).build();
        Feature feature = new Feature("Math", List.of(s1, s2));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-paramedge.html", actual);
    }

    @Test
    void emptyFeatureBrowserHtmlReport_zeroScenariosAlongsidePopulated_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        // A feature with zero scenarios alongside a populated one — the report renders (only an ALL-empty
        // feature set bails out).
        Scenario scenario = Scenario.builder("Logs in", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(120).build();
        Feature populated = new Feature("Login", List.of(scenario));
        Feature empty = new Feature("Empty feature", List.of());

        String actual = DotNetHtmlReportRenderer.render(
            List.of(populated, empty), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-emptyfeature.html", actual);
    }

    @Test
    void parameterizedBrowserHtmlReport_outlineGroupScalarColumns_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        LinkedHashMap<String, String> ev1 = new LinkedHashMap<>();
        ev1.put("input", "5");
        ev1.put("result", "25");
        LinkedHashMap<String, String> ev2 = new LinkedHashMap<>();
        ev2.put("input", "6");
        ev2.put("result", "36");
        Scenario s1 = Scenario.builder("Squares 5", "s1", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Squares").exampleValues(ev1)
            .steps(List.of(new ScenarioStep("Then", "the square is computed", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Scenario s2 = Scenario.builder("Squares 6", "s2", ExecutionStatus.FAILED)
            .durationMs(60).outlineId("Squares").exampleValues(ev2)
            .error("Expected: 36\nActual: 35").errorStackTrace("at Math.Square()")
            .build();
        Feature feature = new Feature("Math", List.of(s1, s2));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-parameterized.html", actual);
    }

    @Test
    void parameterizedBrowserHtmlReport_stringBasedComplexCells_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        LinkedHashMap<String, String> ev1 = new LinkedHashMap<>();
        ev1.put("small", "Item { Id = 5, Name = egg, Price = 3 }");
        ev1.put("big", "Config { A = 1, B = 2, C = 3, D = 4, E = 5, F = 6 }");
        ev1.put("nested", "Order { Id = 1, Who = Person { Name = Bob, Age = 30 } }");
        ev1.put("coll", "Cart { Items = System.Collections.Generic.List`1[MyApp.Models.Item], Total = 10 }");
        ev1.put("plain", "42");
        LinkedHashMap<String, String> ev2 = new LinkedHashMap<>();
        ev2.put("small", "Item { Id = 7, Name = ham }");
        ev2.put("big", "Config { A = 9, B = 8, C = 7, D = 6, E = 5, F = 4, G = 3 }");
        ev2.put("nested", "Order { Id = 2, Who = Person { Name = Sue, Age = 25 } }");
        ev2.put("coll", "Cart { Items = System.Collections.Generic.List`1[MyApp.Models.Item], Total = 20 }");
        ev2.put("plain", "99");
        Scenario s1 = Scenario.builder("Bakes a cake", "s1", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Recipes").exampleValues(ev1)
            .steps(List.of(new ScenarioStep("Then", "it bakes", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Scenario s2 = Scenario.builder("Bakes bread", "s2", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Recipes").exampleValues(ev2)
            .steps(List.of(new ScenarioStep("Then", "it bakes", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Feature feature = new Feature("Recipes", List.of(s1, s2));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-paramcells.html", actual);
    }

    @Test
    void parameterizedBrowserHtmlReport_stringBasedR2Flatten_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        LinkedHashMap<String, String> ev1 = new LinkedHashMap<>();
        ev1.put("order", "Order { Id = 1, Who = Person { Name = Bob, Age = 30 }, Total = 50 }");
        LinkedHashMap<String, String> ev2 = new LinkedHashMap<>();
        ev2.put("order", "Order { Id = 2, Who = Person { Name = Sue, Age = 25 }, Total = 75 }");
        Scenario s1 = Scenario.builder("Order one", "s1", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Orders").exampleValues(ev1)
            .steps(List.of(new ScenarioStep("Then", "it ships", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Scenario s2 = Scenario.builder("Order two", "s2", ExecutionStatus.FAILED)
            .durationMs(60).outlineId("Orders").exampleValues(ev2)
            .error("Expected: shipped\nActual: pending")
            .build();
        Feature feature = new Feature("Fulfilment", List.of(s1, s2));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-r2flatten.html", actual);
    }

    @Test
    void parameterizedBrowserHtmlReport_flattenToggle_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        LinkedHashMap<String, String> ev1 = new LinkedHashMap<>();
        ev1.put("name", "Bob");
        ev1.put("age", "30");
        LinkedHashMap<String, String> fv1 = new LinkedHashMap<>();
        fv1.put("user", "Bob (30)");
        LinkedHashMap<String, String> ev2 = new LinkedHashMap<>();
        ev2.put("name", "Sue");
        ev2.put("age", "25");
        LinkedHashMap<String, String> fv2 = new LinkedHashMap<>();
        fv2.put("user", "Sue (25)");
        Scenario s1 = Scenario.builder("Signup Bob", "s1", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Signup").exampleValues(ev1).exampleFlatValues(fv1)
            .steps(List.of(new ScenarioStep("Then", "account exists", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Scenario s2 = Scenario.builder("Signup Sue", "s2", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Signup").exampleValues(ev2).exampleFlatValues(fv2)
            .steps(List.of(new ScenarioStep("Then", "account exists", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Feature feature = new Feature("Accounts", List.of(s1, s2));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-flattentoggle.html", actual);
    }

    @Test
    void parameterizedBrowserHtmlReport_displayNamePrefixGroup_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        Scenario s1 = Scenario.builder("Login(user: bob, role: admin)", "s1", ExecutionStatus.PASSED)
            .durationMs(50)
            .steps(List.of(new ScenarioStep("Then", "access granted", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Scenario s2 = Scenario.builder("Login(user: sue, role: guest)", "s2", ExecutionStatus.FAILED)
            .durationMs(60).error("Expected: granted\nActual: denied")
            .build();
        Feature feature = new Feature("Security", List.of(s1, s2));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-prefixgroup.html", actual);
    }

    @Test
    void parameterizedBrowserHtmlReport_perExampleAndSharedDiagrams_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        String d1 = "@startuml\nA -> B : one\n@enduml";
        String d2 = "@startuml\nA -> B : two\n@enduml";
        String d3 = "@startuml\nA -> B : three\n@enduml";
        LinkedHashMap<String, String> n1 = new LinkedHashMap<>();
        n1.put("n", "1");
        LinkedHashMap<String, String> n2 = new LinkedHashMap<>();
        n2.put("n", "2");
        LinkedHashMap<String, String> n3 = new LinkedHashMap<>();
        n3.put("n", "3");
        LinkedHashMap<String, String> n4 = new LinkedHashMap<>();
        n4.put("n", "4");
        Scenario s1 = Scenario.builder("Same A", "s1", ExecutionStatus.PASSED)
            .durationMs(10).outlineId("Same").exampleValues(n1).build();
        Scenario s2 = Scenario.builder("Same B", "s2", ExecutionStatus.PASSED)
            .durationMs(20).outlineId("Same").exampleValues(n2).build();
        Scenario s3 = Scenario.builder("Diff A", "s3", ExecutionStatus.PASSED)
            .durationMs(30).outlineId("Diff").exampleValues(n3).build();
        Scenario s4 = Scenario.builder("Diff B", "s4", ExecutionStatus.PASSED)
            .durationMs(40).outlineId("Diff").exampleValues(n4).build();
        Feature feature = new Feature("Flows", List.of(s1, s2, s3, s4));
        Map<String, String> diagramByTestId = new LinkedHashMap<>();
        diagramByTestId.put("s1", d1);
        diagramByTestId.put("s2", d1);
        diagramByTestId.put("s3", d2);
        diagramByTestId.put("s4", d3);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-paramdiagrams.html", actual);
    }

    @Test
    void summaryBrowserHtmlReport_includeTestRunData_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        Scenario login1 = Scenario.builder("Login succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(100)
            .steps(List.of(new ScenarioStep("When", "the user logs in", ExecutionStatus.PASSED, 30L,
                List.of(), List.of())))
            .build();
        Scenario login2 = Scenario.builder("Login fails", "s2", ExecutionStatus.FAILED)
            .durationMs(50).error("bad password").build();
        Feature loginFeature = new Feature("Login", List.of(login1, login2));
        Scenario checkout1 = Scenario.builder("Checkout succeeds", "s3", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(200).build();
        Feature checkoutFeature = new Feature("Checkout", List.of(checkout1));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(loginFeature, checkoutFeature), Map.of(), null, "Kronikol Run", PINNED_VERSION,
            true, Instant.parse("2024-01-15T10:00:00Z"), Instant.parse("2024-01-15T10:00:05Z"));

        assertParity("report-summary.html", actual);
    }

    @Test
    void summaryBrowserHtmlReport_ciMetadata_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        Scenario s1 = Scenario.builder("Login succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(100)
            .steps(List.of(new ScenarioStep("When", "the user logs in", ExecutionStatus.PASSED, 30L,
                List.of(), List.of())))
            .build();
        Scenario s2 = Scenario.builder("Login fails", "s2", ExecutionStatus.FAILED)
            .durationMs(50).error("bad password").build();
        Feature feature = new Feature("Login", List.of(s1, s2));
        CiMetadata ci = new CiMetadata(CiEnvironment.GITHUB_ACTIONS, "42", "main", "abc1234def5678",
            "https://github.com/acme/repo/actions/runs/99", "acme/repo", "99");
        HtmlCustomization custom = new HtmlCustomization(ci, null, null, null, false, false);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION,
            true, Instant.parse("2024-01-15T10:00:00Z"), Instant.parse("2024-01-15T10:00:05Z"), custom);

        assertParity("report-cimetadata.html", actual);
    }

    @Test
    void browserHtmlReport_customCssFaviconLogo_isByteForByteIdenticalToDotNetGolden() throws IOException {
        Scenario s1 = Scenario.builder("Loads home page", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(40)
            .steps(List.of(new ScenarioStep("Then", "the page renders", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Feature feature = new Feature("Web", List.of(s1));
        HtmlCustomization custom = new HtmlCustomization(null, ".kx-banner { color: #c0ffee; }",
            "data:image/png;base64,AAAA", "<img src=\"logo.png\" alt=\"Acme\">", false, false);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION,
            false, Instant.EPOCH, Instant.EPOCH, custom);

        assertParity("report-customassets.html", actual);
    }

    @Test
    void browserHtmlReport_customStyleSheet_isByteForByteIdenticalToDotNetGolden() throws IOException {
        Scenario s1 = Scenario.builder("Renders the spec", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(40)
            .steps(List.of(new ScenarioStep("Then", "the spec renders", ExecutionStatus.PASSED, 10L,
                List.of(), List.of())))
            .build();
        Feature feature = new Feature("Specs", List.of(s1));
        // The .NET stylesheet param goes INTO the main <style> (combinedStylesheet); customCss is a
        // separate trailing <style>. Both set, to prove the placement difference byte-for-byte.
        HtmlCustomization custom = new HtmlCustomization(null, ".kx-after { color: #c0ffee; }",
            null, null, false, false).withCustomStyleSheet(".kx-spec { margin: 0 }\n.kx-spec h2 { color: #336699 }");

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION,
            false, Instant.EPOCH, Instant.EPOCH, custom);

        assertParity("report-customstylesheet.html", actual);
    }

    @Test
    void browserHtmlReport_showStepNumbers_isByteForByteIdenticalToDotNetGolden() throws IOException {
        ScenarioStep subStep = new ScenarioStep("And", "a valid session", ExecutionStatus.PASSED, 2L,
            List.of(), List.of());
        ScenarioStep given = new ScenarioStep("Given", "a logged-in user", ExecutionStatus.PASSED, 10L,
            List.of(subStep), List.of());
        ScenarioStep when = new ScenarioStep("When", "the order is placed", ExecutionStatus.PASSED, 20L,
            List.of(), List.of());
        ScenarioStep then = new ScenarioStep("Then", "it is confirmed", ExecutionStatus.PASSED, 15L,
            List.of(), List.of());
        ScenarioStep bg = new ScenarioStep("Given", "the database is seeded", ExecutionStatus.PASSED, 5L,
            List.of(), List.of());
        Scenario scenario = Scenario.builder("Places an order", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(100)
            .backgroundSteps(List.of(bg)).steps(List.of(given, when, then)).build();
        Feature feature = new Feature("Orders", List.of(scenario));
        HtmlCustomization custom = new HtmlCustomization(null, null, null, null, true, false);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION,
            false, Instant.EPOCH, Instant.EPOCH, custom);

        assertParity("report-stepnumbers.html", actual);
    }

    @Test
    void browserHtmlReport_generateBlankOnFailedTests_isEmptyLikeDotNetGolden() throws IOException {
        Scenario scenario = Scenario.builder("Fails", "s1", ExecutionStatus.FAILED).error("boom").build();
        Feature feature = new Feature("F", List.of(scenario));
        HtmlCustomization custom = new HtmlCustomization(null, null, null, null, false, true);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION,
            false, Instant.EPOCH, Instant.EPOCH, custom);

        assertParity("report-blankonfail.html", actual);
    }

    private static java.util.List<Feature> squaresFeature() {
        LinkedHashMap<String, String> ev1 = new LinkedHashMap<>();
        ev1.put("input", "5");
        ev1.put("result", "25");
        LinkedHashMap<String, String> ev2 = new LinkedHashMap<>();
        ev2.put("input", "6");
        ev2.put("result", "36");
        Scenario s1 = Scenario.builder("Squares 5", "s1", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Squares").exampleValues(ev1).build();
        Scenario s2 = Scenario.builder("Squares 6", "s2", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Squares").exampleValues(ev2).build();
        return List.of(new Feature("Math", List.of(s1, s2)));
    }

    private static String renderParam(java.util.List<Feature> features, ParameterizedOptions opts)
            throws IOException {
        return DotNetHtmlReportRenderer.render(features, Map.of(), null, "Kronikol Run", PINNED_VERSION,
            false, java.time.Instant.EPOCH, java.time.Instant.EPOCH, HtmlCustomization.NONE,
            WholeTestFlowInput.NONE, opts);
    }

    @Test
    void parameterizedBrowserHtmlReport_noTitleize_isByteForByteIdenticalToDotNetGolden() throws IOException {
        assertParity("report-paramnotitleize.html",
            renderParam(squaresFeature(), new ParameterizedOptions(true, 10, false)));
    }

    @Test
    void parameterizedBrowserHtmlReport_maxColumnsFallback_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        assertParity("report-parammaxcols.html",
            renderParam(squaresFeature(), new ParameterizedOptions(true, 1, true)));
    }

    @Test
    void parameterizedBrowserHtmlReport_groupingDisabled_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        Scenario l1 = Scenario.builder("Login(user: bob, role: admin)", "l1", ExecutionStatus.PASSED)
            .durationMs(50).build();
        Scenario l2 = Scenario.builder("Login(user: sue, role: guest)", "l2", ExecutionStatus.PASSED)
            .durationMs(60).build();
        assertParity("report-paramnogroup.html",
            renderParam(List.of(new Feature("Security", List.of(l1, l2))),
                new ParameterizedOptions(false, 10, true)));
    }

    @Test
    void parameterizedBrowserHtmlReport_perExampleWholeTestFlow_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        java.time.Instant t0 = java.time.Instant.parse("2024-01-15T10:00:00Z");
        InternalFlowSegment segA = new InternalFlowSegment("s1", List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /a", t0, 60.0),
            new InternalFlowSpan("2", "1", "Svc", null, "StepA", t0.plusMillis(10), 30.0)));
        InternalFlowSegment segB = new InternalFlowSegment("s2", List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /b", t0, 50.0),
            new InternalFlowSpan("2", "1", "Svc", null, "StepB", t0.plusMillis(5), 20.0)));
        Map<String, InternalFlowSegment> segs = new LinkedHashMap<>();
        segs.put("iflow-test-s1", segA);
        segs.put("iflow-test-s2", segB);
        WholeTestFlowInput wtf = new WholeTestFlowInput(segs, WholeTestFlowVisualization.BOTH, Map.of(), true);

        LinkedHashMap<String, String> n1 = new LinkedHashMap<>();
        n1.put("n", "1");
        LinkedHashMap<String, String> n2 = new LinkedHashMap<>();
        n2.put("n", "2");
        Scenario s1 = Scenario.builder("Recipe A", "s1", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Recipes").exampleValues(n1).build();
        Scenario s2 = Scenario.builder("Recipe B", "s2", ExecutionStatus.PASSED)
            .durationMs(50).outlineId("Recipes").exampleValues(n2).build();
        Feature feature = new Feature("Recipes", List.of(s1, s2));
        Map<String, String> diagramByTestId = new LinkedHashMap<>();
        diagramByTestId.put("s1", "@startuml\nA -> B : seqA\n@enduml");
        diagramByTestId.put("s2", "@startuml\nA -> B : seqB\n@enduml");

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION,
            false, java.time.Instant.EPOCH, java.time.Instant.EPOCH, HtmlCustomization.NONE, wtf);

        assertParity("report-paramwtf.html", actual);
    }

    @Test
    void parameterizedBrowserHtmlReport_sharedWholeTestFlow_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        java.time.Instant t0 = java.time.Instant.parse("2024-01-15T10:00:00Z");
        // Identical span trees → identical flame JSON → allWtfIdentical (FlameChart only).
        List<InternalFlowSpan> spans1 = List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /x", t0, 60.0),
            new InternalFlowSpan("2", "1", "Svc", null, "Step", t0.plusMillis(10), 30.0));
        List<InternalFlowSpan> spans2 = List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /x", t0, 60.0),
            new InternalFlowSpan("2", "1", "Svc", null, "Step", t0.plusMillis(10), 30.0));
        Map<String, InternalFlowSegment> segs = new LinkedHashMap<>();
        segs.put("iflow-test-s3", new InternalFlowSegment("s3", spans1));
        segs.put("iflow-test-s4", new InternalFlowSegment("s4", spans2));
        WholeTestFlowInput wtf =
            new WholeTestFlowInput(segs, WholeTestFlowVisualization.FLAME_CHART, Map.of(), true);

        LinkedHashMap<String, String> n1 = new LinkedHashMap<>();
        n1.put("n", "1");
        LinkedHashMap<String, String> n2 = new LinkedHashMap<>();
        n2.put("n", "2");
        Scenario s3 = Scenario.builder("Shared A", "s3", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Shared").exampleValues(n1).build();
        Scenario s4 = Scenario.builder("Shared B", "s4", ExecutionStatus.PASSED)
            .durationMs(60).outlineId("Shared").exampleValues(n2).build();
        Feature feature = new Feature("Shared", List.of(s3, s4));
        String seq = "@startuml\nA -> B : shared\n@enduml";
        Map<String, String> diagramByTestId = new LinkedHashMap<>();
        diagramByTestId.put("s3", seq);
        diagramByTestId.put("s4", seq);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION,
            false, java.time.Instant.EPOCH, java.time.Instant.EPOCH, HtmlCustomization.NONE, wtf);

        assertParity("report-paramwtfsame.html", actual);
    }

    @Test
    void browserHtmlReport_internalFlowPopup_isByteForByteIdenticalToDotNetGolden() throws IOException {
        java.time.Instant t0 = java.time.Instant.parse("2024-01-15T10:00:00Z");
        InternalFlowSegment segment = new InternalFlowSegment("s1", List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /orders", t0, 100.0),
            new InternalFlowSpan("2", "1", "OrderService", null, "LoadOrder", t0.plusMillis(10), 40.0),
            new InternalFlowSpan("3", "2", "Database", null, "SELECT", t0.plusMillis(15), 20.0),
            new InternalFlowSpan("4", "1", "OrderService", null, "Validate", t0.plusMillis(60), 30.0)),
            "00000000-0000-0000-0000-000000000001", "request");
        Map<String, InternalFlowSegment> perDiagram =
            Map.of("iflow-00000000-0000-0000-0000-000000000001", segment);
        InternalFlowPopupInput popup = new InternalFlowPopupInput(perDiagram,
            InternalFlowDiagramStyle.CALL_TREE, true, InternalFlowFlameChartPosition.BEHIND_WITH_TOGGLE,
            InternalFlowNoDataBehavior.HIDE_LINK, InternalFlowSpanGranularity.AUTO_INSTRUMENTATION, null, 0,
            InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER, true);

        Scenario scenario = Scenario.builder("Order flow", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(100).build();
        Feature feature = new Feature("Orders", List.of(scenario));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION,
            false, java.time.Instant.EPOCH, java.time.Instant.EPOCH, HtmlCustomization.NONE,
            WholeTestFlowInput.NONE, ParameterizedOptions.DEFAULTS, popup);

        assertParity("report-popup.html", actual);
    }

    @Test
    void browserHtmlReport_wholeTestFlowActivityAndFlame_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        java.time.Instant t0 = java.time.Instant.parse("2024-01-15T10:00:00Z");
        InternalFlowSegment segment = new InternalFlowSegment("s1", List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /orders", t0, 100.0),
            new InternalFlowSpan("2", "1", "OrderService", null, "LoadOrder", t0.plusMillis(10), 40.0),
            new InternalFlowSpan("3", "2", "Database", null, "SELECT", t0.plusMillis(15), 20.0),
            new InternalFlowSpan("4", "1", "OrderService", null, "Validate", t0.plusMillis(60), 30.0)));
        WholeTestFlowInput wtf = new WholeTestFlowInput(
            Map.of("iflow-test-s1", segment), WholeTestFlowVisualization.BOTH, Map.of(), true);

        Scenario scenario = Scenario.builder("Order flow", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(100)
            .steps(List.of(new ScenarioStep("When", "the order flows", ExecutionStatus.PASSED, 20L,
                List.of(), List.of())))
            .build();
        Feature feature = new Feature("Orders", List.of(scenario));
        Map<String, String> diagramByTestId = new LinkedHashMap<>();
        diagramByTestId.put("s1", "@startuml\nactor User\nUser -> Service : place\n@enduml");

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION,
            false, java.time.Instant.EPOCH, java.time.Instant.EPOCH, HtmlCustomization.NONE, wtf);

        assertParity("report-wholetestflow.html", actual);
    }

    @Test
    void browserHtmlReport_diagramToolbarToggles_isByteForByteIdenticalToDotNetGolden() throws IOException {
        Scenario scenario = Scenario.builder("Saves order", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(100)
            .steps(List.of(new ScenarioStep("When", "the order is saved", ExecutionStatus.PASSED, 20L,
                List.of(), List.of())))
            .build();
        Feature feature = new Feature("Orders", List.of(scenario));
        String puml = "@startuml\ndatabase \"OrderDB\" as db\nactor User\nUser -> db : "
            + "<<stepDelimiter>> save\nnote over db <<assertionNote>> : verify\n@enduml";
        Map<String, String> diagramByTestId = new LinkedHashMap<>();
        diagramByTestId.put("s1", puml);

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), diagramByTestId, null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-diagramtoggles.html", actual);
    }

    @Test
    void stepDetailsBrowserHtmlReport_commentsAndDocString_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        ScenarioStep step = ScenarioStep.builder("When", "the user submits the order", ExecutionStatus.PASSED)
            .durationMs(40)
            .comments(List.of("verify the payload", "idempotency key sent"))
            .docString("{\n  \"id\": 42,\n  \"total\": \"9.99\"\n}").docStringMediaType("json")
            .build();
        Scenario scenario = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).steps(List.of(step)).build();
        Feature feature = new Feature("Checkout", List.of(scenario));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-stepdetails.html", actual);
    }

    @Test
    void stepSegmentsBrowserHtmlReport_inlineParameters_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        ScenarioStep step = ScenarioStep.builder("Then", "paid 9.99 with code 200", ExecutionStatus.PASSED)
            .durationMs(25)
            .textSegments(List.of(
                StepTextSegment.literal("paid "),
                StepTextSegment.param("amount", new InlineParameterValue("9.99", null, VerificationStatus.NOT_APPLICABLE)),
                StepTextSegment.literal(" with code "),
                StepTextSegment.param("code", new InlineParameterValue("200", "200", VerificationStatus.SUCCESS))))
            .build();
        Scenario scenario = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).steps(List.of(step)).build();
        Feature feature = new Feature("Checkout", List.of(scenario));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-stepsegments.html", actual);
    }

    @Test
    void stepParamsBrowserHtmlReport_inlineParameter_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        ScenarioStep step = ScenarioStep.builder("When", "the amount is charged", ExecutionStatus.PASSED)
            .durationMs(15)
            .parameters(List.of(StepParameter.inline("amount",
                new InlineParameterValue("9.99", null, VerificationStatus.NOT_APPLICABLE))))
            .build();
        Scenario scenario = Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).steps(List.of(step)).build();
        Feature feature = new Feature("Checkout", List.of(scenario));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-stepparams.html", actual);
    }

    @Test
    void stepTablesBrowserHtmlReport_tabularAndTreeParameters_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        StepParameter tabular = StepParameter.tabular("items", new TabularParameterValue(
            List.of(new TabularColumn("name", true), new TabularColumn("qty", false)),
            List.of(
                new TabularRow(TableRowType.MATCHING, List.of(
                    new TabularCell("egg", null, VerificationStatus.NOT_APPLICABLE),
                    new TabularCell("2", null, VerificationStatus.NOT_APPLICABLE))),
                new TabularRow(TableRowType.SURPLUS, List.of(
                    new TabularCell("bonus", null, VerificationStatus.NOT_APPLICABLE),
                    new TabularCell("1", null, VerificationStatus.NOT_APPLICABLE))))));
        StepParameter tree = StepParameter.tree("config", new TreeParameterValue(
            new TreeNode("", "root", "", null, VerificationStatus.NOT_APPLICABLE, List.of(
                new TreeNode("root.a", "a", "1", null, VerificationStatus.SUCCESS, List.of()),
                new TreeNode("root.b", "b", "2", "3", VerificationStatus.FAILURE, List.of())))));
        ScenarioStep whenStep = ScenarioStep.builder("When", "the cart is loaded", ExecutionStatus.PASSED)
            .durationMs(20).parameters(List.of(tabular)).build();
        ScenarioStep thenStep = ScenarioStep.builder("Then", "the config matches", ExecutionStatus.PASSED)
            .durationMs(30).parameters(List.of(tree)).build();
        Scenario scenario = Scenario.builder("Data is processed", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).steps(List.of(whenStep, thenStep)).build();
        Feature feature = new Feature("Data", List.of(scenario));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-steptables.html", actual);
    }

    @Test
    void combinedTableBrowserHtmlReport_setupAndAssertionTables_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        StepParameter setup = StepParameter.tabular("inputs", new TabularParameterValue(
            List.of(new TabularColumn("id", true), new TabularColumn("name", false)),
            List.of(
                new TabularRow(TableRowType.MATCHING, List.of(
                    new TabularCell("1", null, VerificationStatus.NOT_APPLICABLE),
                    new TabularCell("egg", null, VerificationStatus.NOT_APPLICABLE))),
                new TabularRow(TableRowType.MATCHING, List.of(
                    new TabularCell("2", null, VerificationStatus.NOT_APPLICABLE),
                    new TabularCell("milk", null, VerificationStatus.NOT_APPLICABLE))))));
        StepParameter assertion = StepParameter.tabular("outputs", new TabularParameterValue(
            List.of(new TabularColumn("id", true), new TabularColumn("total", false)),
            List.of(
                new TabularRow(TableRowType.MATCHING, List.of(
                    new TabularCell("1", null, VerificationStatus.SUCCESS),
                    new TabularCell("9.99", null, VerificationStatus.SUCCESS))),
                new TabularRow(TableRowType.MATCHING, List.of(
                    new TabularCell("2", null, VerificationStatus.SUCCESS),
                    new TabularCell("4.99", null, VerificationStatus.SUCCESS))))));
        ScenarioStep given = ScenarioStep.builder("Given", "the cart is set up", ExecutionStatus.PASSED)
            .durationMs(20).parameters(List.of(setup)).build();
        ScenarioStep then = ScenarioStep.builder("Then", "the totals are correct", ExecutionStatus.PASSED)
            .durationMs(30).parameters(List.of(assertion)).build();
        Scenario scenario = Scenario.builder("Cart totals", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).steps(List.of(given, then)).build();
        Feature feature = new Feature("Cart", List.of(scenario));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-combinedtable.html", actual);
    }

    @Test
    void complexParamsBrowserHtmlReport_textRefToComplexInline_isByteForByteIdenticalToDotNetGolden()
            throws IOException {
        ScenarioStep step = ScenarioStep.builder("Then", "small and large", ExecutionStatus.PASSED)
            .durationMs(25)
            .textSegments(List.of(
                StepTextSegment.literal("small "),
                StepTextSegment.tableRef("item"),
                StepTextSegment.literal(" large "),
                StepTextSegment.tableRef("config")))
            .parameters(List.of(
                StepParameter.inline("item", new InlineParameterValue(
                    "Item { Id = 5, Name = egg }", null, VerificationStatus.NOT_APPLICABLE)),
                StepParameter.inline("config", new InlineParameterValue(
                    "Config { A = 1, B = 2, C = 3, D = 4, E = 5 }", null, VerificationStatus.NOT_APPLICABLE))))
            .build();
        Scenario scenario = Scenario.builder("Order is placed", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).durationMs(1500).steps(List.of(step)).build();
        Feature feature = new Feature("Orders", List.of(scenario));

        String actual = DotNetHtmlReportRenderer.render(
            List.of(feature), Map.of(), null, "Kronikol Run", PINNED_VERSION);

        assertParity("report-complexparams.html", actual);
    }

    /** Asserts byte-identity (outside the gzip puml-data) and decoded-equality of the puml-data. */
    private static void assertParity(String goldenName, String actual) throws IOException {
        Path dump = Path.of("build", "parity", goldenName.replace(".html", ".actual.html"));
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, actual, StandardCharsets.UTF_8);

        String golden = readGolden(goldenName);
        assertEquals(mask(golden), mask(actual),
            "HTML differs outside the puml-data / data-flame-z payloads — see " + dump.toAbsolutePath());
        assertEquals(decodePumlData(golden), decodePumlData(actual),
            "puml-data decodes differently between golden and actual (" + goldenName + ")");
        assertEquals(decodeFlameZ(golden), decodeFlameZ(actual),
            "data-flame-z decodes differently between golden and actual (" + goldenName + ")");
    }

    private static String readGolden(String name) {
        try (InputStream in = GoldenHtmlParityTest.class.getResourceAsStream("/parity/" + name)) {
            assertTrue(in != null, "golden fixture /parity/" + name + " not found on test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Replaces the gzip payloads (puml-data island + every {@code data-flame-z}) with placeholders so
     *  the rest can be byte-compared (gzip is not byte-stable across runtimes). */
    private static String mask(String html) {
        String masked = FLAME_Z.matcher(html).replaceAll("data-flame-z=\"__FLAME_Z__\"");
        int start = masked.indexOf(PUML_OPEN);
        if (start < 0) {
            return masked;
        }
        int contentStart = start + PUML_OPEN.length();
        int end = masked.indexOf("</script>", contentStart);
        return masked.substring(0, contentStart) + "__PUML_DATA__" + masked.substring(end);
    }

    /** The decoded (base64+gunzip) flame-chart JSON payloads, in document order. */
    private static List<String> decodeFlameZ(String html) {
        List<String> decoded = new ArrayList<>();
        Matcher m = FLAME_Z.matcher(html);
        while (m.find()) {
            decoded.add(gunzip(Base64.getDecoder().decode(m.group(1))));
        }
        return decoded;
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
