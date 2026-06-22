// Golden-file capture harness: drives the REAL .NET Kronikol PlantUmlCreator over fixed,
// deterministic corpora (fixed GUIDs) and writes the plain PlantUML to ./fixtures/*.puml.
// The Java parity tests assert (after normalising only the trailing newline) byte-equality.

using System.Diagnostics;
using System.Net;
using Kronikol;
using Kronikol.ComponentDiagram;
using Kronikol.InternalFlow;
using Kronikol.PlantUml;
using Kronikol.Reports;
using Kronikol.Tracking;

var outDir = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "fixtures"));
Directory.CreateDirectory(outDir);

// Component diagram (browser path → useC4:false), default options (DependencyType arrow colours).
CaptureComponent("component", FanOut());

// Test-run report data in all three formats (rich corpus: steps, attachments, examples, diagrams,
// httpInteractions; fixed times/ids so the fixtures are reproducible).
CaptureReportData();

// Full browser-render HTML report (Stage 0 evidence: scope true byte-parity from the real output).
CaptureHtml();

// Richer browser-render HTML: a component diagram + a failed scenario alongside the passed one
// (exercises the component-diagram head/body wiring, the failure-result block, jump-to-failure,
// status filters with failures, and mixed-status timeline — so the migrated tests prove those paths).
CaptureHtmlRich();

// BDD steps: a scenario with Background Steps + Steps (incl. a nested sub-step), exercising the
// scenario-background/scenario-steps <details>, RenderStep status/keyword/duration and sub-step recursion.
CaptureHtmlSteps();

// Skipped + bypassed scenarios alongside a passed one: data-status values, h3 summary classes,
// status tooltips, and the mixed-status timeline bars (passed/skipped/bypassed).
CaptureHtmlStatuses();

// Attachments: a scenario-level attachments block (image vs link) + a step with step-level
// attachments (the lightbox image variant + a plain link).
CaptureHtmlAttachments();

// Rule grouping: a null-rule (happy-path) scenario with no wrapper, two scenarios sharing a Rule,
// and a rule change — exercising the <details class="rule"> open/close around consecutive scenarios.
CaptureHtmlRules();

// ErrorDiffParser: a failed scenario whose message matches the xUnit Expected/Actual shape, so the
// failure-result block carries the character-level diff table (error-diff / diff-del / diff-ins).
CaptureHtmlErrorDiff();

// Parameterized group: two scenarios sharing an OutlineId with scalar ExampleValues — the
// scenario-parameterized aggregate <details> + the ScalarColumns param table.
CaptureHtmlParameterized();

// Escaping audit: every user-text field carries the markup-trigger marker <b>&"' so any report call
// site that emits raw (or over-escapes) shows up as a byte diff against the real WebUtility output.
CaptureHtmlEscaping();
CaptureHtmlEscapingParams();

// Optional-field edge branches the timed goldens never hit: a scenario with NO Duration (no badge/attr)
// and steps with NO Status / NO Duration (Duration.HasValue / Status.HasValue == false).
CaptureHtmlEdgeFields();
// FormatDisplayValue edge branches: a literal "null" value and a whitespace-only value in a param table.
CaptureHtmlParamEdgeValues();
// A feature with zero scenarios alongside a populated one (the report renders, it does not bail).
CaptureHtmlEmptyFeature();
// Scenario Categories → category-filters box; diagram toggle markers → Assertions/Steps/Databases buttons.
CaptureHtmlFiltersToggles();

// includeTestRunData=true: the Features Summary table (conditional Steps/Duration columns), the
// Test Execution Summary, the pie chart, and the header-row wrapping.
CaptureHtmlSummary();

// Rich step rendering — a step with Comments + a DocString (with media type).
CaptureHtmlStepDetails();

// Rich step rendering — structured TextSegments (literal prose + highlighted inline parameter values).
CaptureHtmlStepSegments();

// Rich step rendering — a step Parameters list (inline kind) rendered via RenderParameter.
CaptureHtmlStepParams();

// Rich step rendering — tabular + tree step parameters (step-param-table / step-param-tree).
CaptureHtmlStepTables();

// Rich step rendering — the combined setup+assertion tabular table (key-aligned).
CaptureHtmlCombinedTable();

// Rich step rendering — TextSegments table-references to complex inline params (small → inline
// summary; large → expandable button + JSON), exercising the string-based ParameterParser.
CaptureHtmlComplexParams();

// Parameterized table cells — ExampleValues whose strings are .NET record ToString() shapes, so the
// string-based R3 (cell-subtable) / R4 (param-expand) path fires (no ExampleRawValues set), plus a
// nested record (recursion) and a collection type name (TryCleanCollectionTypeName) and a scalar.
CaptureHtmlParamComplexCells();

// String-based R2 FlattenedObject — a single record-string param flattened into per-property columns
// (TryStringBasedFlatten), with a nested-record column rendered as an R3 cell-subtable.
CaptureHtmlR2Flatten();

// Flatten toggle — ExampleFlatValues + ExampleValues produce the param-table-wrapper with a visible
// param-table-flat table (− toggle) and a hidden param-table-grouped table (+ toggle).
CaptureHtmlFlattenToggle();

// Display-name prefix grouping — non-OutlineId scenarios sharing a base name, with params encoded in
// the display name (ExtractBaseName + Parse → ScalarColumns).
CaptureHtmlPrefixGroup();

// Per-example sequence diagrams in parameterized groups — one shared diagram + identical badge when
// all examples match (AllDiagramsIdentical), else one diagram per example.
CaptureHtmlParamDiagrams();

// CI metadata block — the ci-chart-group wrapping the ci-metadata table (Provider/Build/Branch/
// Commit/Pipeline/Repository) and the pie chart, in the test-run-data summary.
CaptureHtmlCiMetadata();

// Custom assets — customCss (<style> after the main stylesheet), customFaviconBase64 (favicon href),
// customLogoHtml (custom-logo div above the <h1>).
CaptureHtmlCustomAssets();

// stylesheet (HtmlSpecificationsCustomStyleSheet) — appended INTO the main <style> right after the base
// stylesheet (combinedStylesheet), distinct from the trailing customCss <style>.
CaptureHtmlCustomStyleSheet();

// CI markdown summary (CiSummaryGenerator.GenerateMarkdown) — the metrics table + failed-scenario
// details (error/stack-trace/escaping) is byte-stable; the diagram case has a deflate-encoded server URL.
CaptureCiSummaryFailed();
CaptureCiSummaryDiagrams();
CaptureCiSummaryMultipart();

// Diagnostic report (DiagnosticReportGenerator.BuildHtml) — the deterministic sections driven by
// logs + features + config (log summary, per-service/per-test, unknown entries, unpaired, orphaned,
// no-log scenarios, activity-span count). The runtime-registry sections are empty in the harness.
CaptureDiagnosticReport();

// ReportDiagnostics.Analyse — the deterministic console warnings (log counts, unpaired, orphaned, the
// 0-spans note). Captured as the newline-joined array; the runtime-store warnings are empty here.
CaptureReportDiagnostics();

// Report-data JSON Schema (GenerateTestRunReportJsonSchema) — the static draft-2020-12 schema describing
// TestRunReport.json (also used for the YAML format).
CaptureReportDataSchema();

// showStepNumbers — background/scenario step number prefixes (1., 2., …) incl. nested sub-steps (1.1.).
CaptureHtmlStepNumbers();

// generateBlankOnFailedTests — an empty report file when any scenario failed.
CaptureHtmlBlankOnFail();

// Diagram-toolbar toggles — a diagram with assertion-note / step-delimiter / database markers renders
// the Assertions/Steps/Databases buttons in the global toolbar and the scenario diagram bar.
CaptureHtmlDiagramToggles();

// Internal-flow activity diagram — the raw PlantUML produced by InternalFlowRenderer for a nested
// span tree (swimlanes per source, duration suffixes). Dumped with native (CRLF) line endings, since
// that is what the report gzips into puml-data before its ReplaceLineEndings("\n").
DumpInternalFlowActivity();

// Internal-flow call tree — the iflow-call-tree HTML list (nested <li> with source/duration spans).
DumpInternalFlowCallTree();

// Internal-flow popup segment-data script — window.__iflowSegments (CallTree + flame; no gzip).
DumpInternalFlowSegmentData();

// Interactive internal-flow popup in a full report — internalFlowTracking + window.__iflowConfig +
// window.__iflowSegments (CallTree style → byte-comparable) in the head.
CaptureHtmlPopup();

// Internal-flow flame data — the compact System.Text.Json produced by GetFlameChartDataWithMarkers
// (fractional percentages exercise the double formatting + banker's rounding). Single-line JSON.
DumpInternalFlowFlame();

// Whole-test-flow in the report — a scenario with a sequence diagram + activity + flame (Both), the
// multi-view diagram toolbar, and the internalFlowTracking head scripts.
CaptureHtmlWholeTestFlow();

// Whole-test-flow in a PARAMETERIZED group — two examples with different per-example sequence diagrams
// + different activity/flame (per-example diagram-view divs).
CaptureHtmlParamWholeTestFlow();

// Parameterized option flags (non-default) — titleizeParameterNames:false / maxParameterColumns:1 /
// groupParameterizedTests:false.
CaptureHtmlParamOptions();

// Whole-test-flow in a PARAMETERIZED group, all examples identical (shared seq + shared flame +
// "identical across test cases" badge; FlameChart-only so allWtfIdentical can hold).
CaptureHtmlParamWholeTestFlowSame();


void CaptureHtml()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500)
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [scenario] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "",
            "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml")
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report.html"), content);
    Console.WriteLine($"=== report.html ({content.Length} chars) ===");

    // Stage 1 (§4.2): dump the exact embedded CSS so it can be externalized byte-identically.
    File.WriteAllText(Path.Combine(outDir, "stylesheets.css"),
        Stylesheets.HtmlReportStyleSheet.ReplaceLineEndings("\n"));
    Console.WriteLine($"=== stylesheets.css ({Stylesheets.HtmlReportStyleSheet.Length} chars) ===");

    // Dump the 9 pure-static DiagramContextMenu assets (for §4.2 externalization).
    var assetDir = Path.Combine(outDir, "assets");
    Directory.CreateDirectory(assetDir);
    void Dump(string name, string content) =>
        File.WriteAllText(Path.Combine(assetDir, name), content.ReplaceLineEndings("\n"));
    Dump("context-menu-styles.css", DiagramContextMenu.GetStyles());
    Dump("inline-svg-styles.css", DiagramContextMenu.GetInlineSvgStyles());
    Dump("collapsible-notes-styles.css", DiagramContextMenu.GetCollapsibleNotesStyles());
    Dump("internal-flow-popup-styles.css", DiagramContextMenu.GetInternalFlowPopupStyles());
    Dump("context-menu-script.js", DiagramContextMenu.GetContextMenuScript());
    Dump("internal-flow-popup-script.js", DiagramContextMenu.GetInternalFlowPopupScript());
    Dump("toggle-script.js", DiagramContextMenu.GetToggleScript());
    Dump("flame-chart-render-script.js", DiagramContextMenu.GetFlameChartRenderScript());
    Dump("collapsible-notes-script.js", DiagramContextMenu.GetCollapsibleNotesScript());
    // Interpolated: dump the rendered script, then templatize the CDN back to a placeholder.
    var cdn = "https://cdn.jsdelivr.net/gh/lemonlion/plantuml-js-plantuml_limit_size_98304@v1.2026.3beta6-patched";
    var browserScript = DiagramContextMenu.GetPlantUmlBrowserRenderScript().ReplaceLineEndings("\n")
        .Replace(cdn, "__PLANTUML_CDN_BASE__");
    File.WriteAllText(Path.Combine(assetDir, "plantuml-browser-render-script.js"), browserScript);
    Console.WriteLine("=== dumped 9 + browser-render assets ===");
}

void CaptureHtmlRich()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var passed = new Scenario
    {
        Id = "s1", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500)
    };
    var failed = new Scenario
    {
        Id = "s2", DisplayName = "Checkout rejects empty cart", IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(12),
        ErrorMessage = "Expected <400> but got <500> & failed",
        ErrorStackTrace = "at Checkout.Validate()\n  at Checkout.Run()"
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [passed, failed] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "",
            "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml"),
        new DefaultDiagramsFetcher.DiagramAsCode("s2", "",
            "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml")
    };
    var componentDiagram = "@startuml\n[Test] --> [OrderService] : HTTP\n@enduml";
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-rich.html", "Kronikol Run",
        includeTestRunData: false, componentDiagramPlantUml: componentDiagram);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-rich.html"), content);
    Console.WriteLine($"=== report-rich.html ({content.Length} chars) ===");
}

void CaptureHtmlSteps()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        BackgroundSteps =
        [
            new ScenarioStep { Keyword = "Given", Text = "a logged-in user", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) }
        ],
        Steps =
        [
            new ScenarioStep { Keyword = "Given", Text = "an empty cart", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20) },
            new ScenarioStep
            {
                Keyword = "When", Text = "the user checks out", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(500),
                SubSteps = [ new ScenarioStep { Text = "POST /checkout", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(400) } ]
            },
            new ScenarioStep { Keyword = "Then", Text = "the order is confirmed", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30) }
        ]
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [scenario] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "",
            "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml")
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-steps.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-steps.html"), content);
    Console.WriteLine($"=== report-steps.html ({content.Length} chars) ===");
}

void CaptureHtmlStatuses()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var passed = new Scenario
    {
        Id = "s1", DisplayName = "Checkout passes", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500)
    };
    var skipped = new Scenario
    {
        Id = "s2", DisplayName = "Checkout is skipped", IsHappyPath = false,
        Result = ExecutionResult.Skipped, Duration = TimeSpan.FromMilliseconds(800)
    };
    var bypassed = new Scenario
    {
        Id = "s3", DisplayName = "Checkout is bypassed", IsHappyPath = false,
        Result = ExecutionResult.Bypassed, Duration = TimeSpan.FromMilliseconds(600)
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [passed, skipped, bypassed] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "",
            "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml")
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-statuses.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-statuses.html"), content);
    Console.WriteLine($"=== report-statuses.html ({content.Length} chars) ===");
}

void CaptureHtmlAttachments()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Steps =
        [
            new ScenarioStep
            {
                Keyword = "Then", Text = "the order is confirmed", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30),
                Attachments = [ new FileAttachment("step-shot.png", "attachments/step-shot.png"), new FileAttachment("log.txt", "attachments/log.txt") ]
            }
        ],
        Attachments = [ new FileAttachment("receipt.pdf", "attachments/receipt.pdf"), new FileAttachment("screenshot.png", "attachments/screenshot.png") ]
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [scenario] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "",
            "@startuml\nactor Test\nTest -> OrderService : POST: /checkout\n@enduml")
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-attachments.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-attachments.html"), content);
    Console.WriteLine($"=== report-attachments.html ({content.Length} chars) ===");
}

void CaptureHtmlRules()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var happy = new Scenario { Id = "s0", DisplayName = "Happy checkout", IsHappyPath = true, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500) };
    var a = new Scenario { Id = "s1", DisplayName = "Adds item", IsHappyPath = false, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100), Rule = "Cart rules" };
    var b = new Scenario { Id = "s2", DisplayName = "Browses catalog", IsHappyPath = false, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(200), Rule = "Cart rules" };
    var c = new Scenario { Id = "s3", DisplayName = "Checks out", IsHappyPath = false, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(300), Rule = "Checkout rules" };
    var feature = new Feature { DisplayName = "Shopping", Scenarios = [happy, a, b, c] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-rules.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-rules.html"), content);
    Console.WriteLine($"=== report-rules.html ({content.Length} chars) ===");
}

void CaptureHtmlErrorDiff()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var failed = new Scenario
    {
        Id = "s1", DisplayName = "Checkout validates total", IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(15),
        ErrorMessage = "Expected: 400\nActual: 500",
        ErrorStackTrace = "at Checkout.Validate()"
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [failed] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-errordiff.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-errordiff.html"), content);
    Console.WriteLine($"=== report-errordiff.html ({content.Length} chars) ===");
}

void CaptureHtmlParameterized()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Squares 5", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50),
        OutlineId = "Squares", ExampleValues = new() { ["input"] = "5", ["result"] = "25" },
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "the square is computed", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Squares 6", IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(60),
        OutlineId = "Squares", ExampleValues = new() { ["input"] = "6", ["result"] = "36" },
        ErrorMessage = "Expected: 36\nActual: 35", ErrorStackTrace = "at Math.Square()"
    };
    var feature = new Feature { DisplayName = "Math", Scenarios = [s1, s2] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-parameterized.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-parameterized.html"), content);
    Console.WriteLine($"=== report-parameterized.html ({content.Length} chars) ===");
}

void CaptureHtmlEscaping()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    const string M = "<b>&\"'"; // markup-trigger → &lt;b&gt;&amp;&quot;&#39; wherever a call site escapes
    var passed = new Scenario
    {
        Id = "s1", DisplayName = "Adds " + M, IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Rule = "Rule " + M,
        Steps =
        [
            new ScenarioStep
            {
                Keyword = "Given " + M, Text = "a step " + M, Status = ExecutionResult.Passed,
                Duration = TimeSpan.FromMilliseconds(20),
                SubSteps = [ new ScenarioStep { Text = "substep " + M, Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(5) } ],
                Attachments = [ new FileAttachment("file " + M, "attachments/x.png") ]
            }
        ],
        Attachments = [ new FileAttachment("att " + M, "attachments/y.pdf") ]
    };
    var failed = new Scenario
    {
        Id = "s2", DisplayName = "Rejects " + M, IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(12),
        ErrorMessage = "Error " + M, ErrorStackTrace = "at " + M
    };
    var feature = new Feature { DisplayName = "Feature " + M, Scenarios = [passed, failed] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "", "@startuml\nactor Test\nTest -> X : POST\n@enduml"),
        new DefaultDiagramsFetcher.DiagramAsCode("s2", "", "@startuml\nactor Test\nTest -> X : POST\n@enduml")
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-escaping.html", "Title " + M, includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-escaping.html"), content);
    Console.WriteLine($"=== report-escaping.html ({content.Length} chars) ===");
}

void CaptureHtmlFiltersToggles()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // Triggers the untested toolbar/filter branches: scenario Categories → the category-filters box, and
    // a diagram carrying all three toggle markers → the Assertions/Steps/Databases toolbar buttons.
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Rich diagram", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100),
        Categories = ["Smoke", "Regression"]
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [scenario] };
    var diagram = "@startuml\nactor Test\ndatabase \"OrderDb\" as orderDb\nTest -> orderDb : SELECT\n"
        + "note<<assertionNote>> right\nassert ok\nend note\nnote<<stepDelimiter>> right\n-- step --\nend note\n@enduml";
    var diagrams = new[] { new DefaultDiagramsFetcher.DiagramAsCode("s1", "", diagram) };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-filterstoggles.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-filterstoggles.html"), content);
    Console.WriteLine($"=== report-filterstoggles.html ({content.Length} chars) ===");
}

void CaptureHtmlEmptyFeature()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // One populated feature + one with zero scenarios (the report does NOT bail — only an ALL-empty set
    // does, line 57). Exercises how an empty feature section is rendered alongside a populated one.
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Logs in", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(120)
    };
    var populated = new Feature { DisplayName = "Login", Scenarios = [scenario] };
    var empty = new Feature { DisplayName = "Empty feature", Scenarios = [] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [populated, empty], start, end, null, "report-emptyfeature.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-emptyfeature.html"), content);
    Console.WriteLine($"=== report-emptyfeature.html ({content.Length} chars) ===");
}

void CaptureHtmlParamEdgeValues()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // FormatDisplayValue edge branches: a literal "null" → <pre>null</pre>, a whitespace-only value →
    // <pre>{ws}</pre>, alongside a normal value (escaped inline).
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Edge A", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50),
        OutlineId = "Edges", ExampleValues = new() { ["nullish"] = "null", ["blank"] = "   ", ["normal"] = "42" }
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Edge B", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60),
        OutlineId = "Edges", ExampleValues = new() { ["nullish"] = "x", ["blank"] = "y", ["normal"] = "99" }
    };
    var feature = new Feature { DisplayName = "Math", Scenarios = [s1, s2] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-paramedge.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-paramedge.html"), content);
    Console.WriteLine($"=== report-paramedge.html ({content.Length} chars) ===");
}

void CaptureHtmlEdgeFields()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // A skipped scenario with NO Duration (Duration.HasValue == false → no badge/attr), plus a structural
    // step with NO Status and NO Duration alongside a normal timed step.
    var skipped = new Scenario
    {
        Id = "s1", DisplayName = "Skipped no duration", IsHappyPath = false,
        Result = ExecutionResult.Skipped,
        Steps =
        [
            new ScenarioStep { Keyword = "Given", Text = "a structural step" },
            new ScenarioStep { Keyword = "When", Text = "a timed step", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(40) }
        ]
    };
    var feature = new Feature { DisplayName = "Edges", Scenarios = [skipped] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-edgefields.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-edgefields.html"), content);
    Console.WriteLine($"=== report-edgefields.html ({content.Length} chars) ===");
}

void CaptureHtmlEscapingParams()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    const string M = "<b>&\"'";
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Case A", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50),
        OutlineId = "Outline " + M, ExampleValues = new() { ["in " + M] = "v1 " + M, ["out"] = "r1" }
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Case B", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60),
        OutlineId = "Outline " + M, ExampleValues = new() { ["in " + M] = "v2 " + M, ["out"] = "r2" }
    };
    var feature = new Feature { DisplayName = "Math", Scenarios = [s1, s2] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-escaping-params.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-escaping-params.html"), content);
    Console.WriteLine($"=== report-escaping-params.html ({content.Length} chars) ===");
}

void CaptureHtmlParamOptions()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // A two-key OutlineId group, captured three ways to exercise the option flags.
    Func<Scenario[]> squares = () => new[]
    {
        new Scenario { Id = "s1", DisplayName = "Squares 5", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50), OutlineId = "Squares", ExampleValues = new() { ["input"] = "5", ["result"] = "25" } },
        new Scenario { Id = "s2", DisplayName = "Squares 6", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60), OutlineId = "Squares", ExampleValues = new() { ["input"] = "6", ["result"] = "36" } }
    };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();

    // titleizeParameterNames:false → raw "input"/"result" sub-headers (not "Input"/"Result").
    var f1 = new Feature { DisplayName = "Math", Scenarios = squares() };
    var p1 = ReportGenerator.GenerateHtmlReport(diagrams, [f1], start, end, null, "report-paramnotitleize.html", "Kronikol Run", includeTestRunData: false, titleizeParameterNames: false);
    File.WriteAllText(Path.Combine(outDir, "report-paramnotitleize.html"), File.ReadAllText(p1).ReplaceLineEndings("\n"));
    Console.WriteLine("=== report-paramnotitleize.html ===");

    // maxParameterColumns:1 → 2 keys > 1 → Fallback single "Test Case" column.
    var f2 = new Feature { DisplayName = "Math", Scenarios = squares() };
    var p2 = ReportGenerator.GenerateHtmlReport(diagrams, [f2], start, end, null, "report-parammaxcols.html", "Kronikol Run", includeTestRunData: false, maxParameterColumns: 1);
    File.WriteAllText(Path.Combine(outDir, "report-parammaxcols.html"), File.ReadAllText(p2).ReplaceLineEndings("\n"));
    Console.WriteLine("=== report-parammaxcols.html ===");

    // groupParameterizedTests:false → non-OutlineId prefix scenarios render individually.
    var l1 = new Scenario { Id = "l1", DisplayName = "Login(user: bob, role: admin)", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50) };
    var l2 = new Scenario { Id = "l2", DisplayName = "Login(user: sue, role: guest)", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60) };
    var f3 = new Feature { DisplayName = "Security", Scenarios = [l1, l2] };
    var p3 = ReportGenerator.GenerateHtmlReport(diagrams, [f3], start, end, null, "report-paramnogroup.html", "Kronikol Run", includeTestRunData: false, groupParameterizedTests: false);
    File.WriteAllText(Path.Combine(outDir, "report-paramnogroup.html"), File.ReadAllText(p3).ReplaceLineEndings("\n"));
    Console.WriteLine("=== report-paramnogroup.html ===");
}

void CaptureHtmlParamWholeTestFlow()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);
    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("Svc");
    var a1 = reqSrc.StartActivity("GET /a")!; a1.SetStartTime(t0);
    var a2 = svcSrc.StartActivity("StepA")!; a2.SetStartTime(t0.AddMilliseconds(10)); a2.SetEndTime(t0.AddMilliseconds(40)); a2.Stop();
    a1.SetEndTime(t0.AddMilliseconds(60)); a1.Stop();
    var segA = new InternalFlowSegment(Guid.Empty, RequestResponseType.Request, "s1",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(60), TimeSpan.Zero), new[] { a1, a2 });
    var b1 = reqSrc.StartActivity("GET /b")!; b1.SetStartTime(t0);
    var b2 = svcSrc.StartActivity("StepB")!; b2.SetStartTime(t0.AddMilliseconds(5)); b2.SetEndTime(t0.AddMilliseconds(25)); b2.Stop();
    b1.SetEndTime(t0.AddMilliseconds(50)); b1.Stop();
    var segB = new InternalFlowSegment(Guid.Empty, RequestResponseType.Request, "s2",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(50), TimeSpan.Zero), new[] { b1, b2 });
    var wholeTestSegments = new Dictionary<string, InternalFlowSegment> { ["iflow-test-s1"] = segA, ["iflow-test-s2"] = segB };

    var s1 = new Scenario { Id = "s1", DisplayName = "Recipe A", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60), OutlineId = "Recipes", ExampleValues = new() { ["n"] = "1" } };
    var s2 = new Scenario { Id = "s2", DisplayName = "Recipe B", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50), OutlineId = "Recipes", ExampleValues = new() { ["n"] = "2" } };
    var feature = new Feature { DisplayName = "Recipes", Scenarios = [s1, s2] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "", "@startuml\nA -> B : seqA\n@enduml"),
        new DefaultDiagramsFetcher.DiagramAsCode("s2", "", "@startuml\nA -> B : seqB\n@enduml")
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-paramwtf.html", "Kronikol Run", includeTestRunData: false,
        internalFlowTracking: true, wholeTestSegments: wholeTestSegments,
        wholeTestVisualization: WholeTestFlowVisualization.Both);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-paramwtf.html"), content);
    Console.WriteLine($"=== report-paramwtf.html ({content.Length} chars) ===");
}

void CaptureHtmlParamWholeTestFlowSame()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);
    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("Svc");
    // Identical span trees for both examples → identical flame JSON → allWtfIdentical (FlameChart only,
    // so the testId-bearing activity html is absent and cannot diverge).
    InternalFlowSegment BuildSeg(string testId)
    {
        var r = reqSrc.StartActivity("GET /x")!; r.SetStartTime(t0);
        var st = svcSrc.StartActivity("Step")!; st.SetStartTime(t0.AddMilliseconds(10)); st.SetEndTime(t0.AddMilliseconds(40)); st.Stop();
        r.SetEndTime(t0.AddMilliseconds(60)); r.Stop();
        return new InternalFlowSegment(Guid.Empty, RequestResponseType.Request, testId,
            new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(60), TimeSpan.Zero), new[] { r, st });
    }
    var wholeTestSegments = new Dictionary<string, InternalFlowSegment> { ["iflow-test-s3"] = BuildSeg("s3"), ["iflow-test-s4"] = BuildSeg("s4") };

    var s3 = new Scenario { Id = "s3", DisplayName = "Shared A", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60), OutlineId = "Shared", ExampleValues = new() { ["n"] = "1" } };
    var s4 = new Scenario { Id = "s4", DisplayName = "Shared B", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60), OutlineId = "Shared", ExampleValues = new() { ["n"] = "2" } };
    var feature = new Feature { DisplayName = "Shared", Scenarios = [s3, s4] };
    // Same sequence diagram for both → group.AllDiagramsIdentical.
    var seq = "@startuml\nA -> B : shared\n@enduml";
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s3", "", seq),
        new DefaultDiagramsFetcher.DiagramAsCode("s4", "", seq)
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-paramwtfsame.html", "Kronikol Run", includeTestRunData: false,
        internalFlowTracking: true, wholeTestSegments: wholeTestSegments,
        wholeTestVisualization: WholeTestFlowVisualization.FlameChart);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-paramwtfsame.html"), content);
    Console.WriteLine($"=== report-paramwtfsame.html ({content.Length} chars) ===");
}

void CaptureHtmlWholeTestFlow()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);
    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("OrderService");
    using var dbSrc = new ActivitySource("Database");
    var root = reqSrc.StartActivity("GET /orders")!; root.SetStartTime(t0);
    var load = svcSrc.StartActivity("LoadOrder")!; load.SetStartTime(t0.AddMilliseconds(10));
    var sel = dbSrc.StartActivity("SELECT")!; sel.SetStartTime(t0.AddMilliseconds(15)); sel.SetEndTime(t0.AddMilliseconds(35)); sel.Stop();
    load.SetEndTime(t0.AddMilliseconds(50)); load.Stop();
    var val = svcSrc.StartActivity("Validate")!; val.SetStartTime(t0.AddMilliseconds(60)); val.SetEndTime(t0.AddMilliseconds(90)); val.Stop();
    root.SetEndTime(t0.AddMilliseconds(100)); root.Stop();
    var segment = new InternalFlowSegment(Guid.Empty, RequestResponseType.Request, "s1",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(100), TimeSpan.Zero),
        new[] { root, load, sel, val });
    var wholeTestSegments = new Dictionary<string, InternalFlowSegment> { ["iflow-test-s1"] = segment };

    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Order flow", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100),
        Steps = [ new ScenarioStep { Keyword = "When", Text = "the order flows", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20) } ]
    };
    var feature = new Feature { DisplayName = "Orders", Scenarios = [scenario] };
    var diagrams = new[] { new DefaultDiagramsFetcher.DiagramAsCode("s1", "", "@startuml\nactor User\nUser -> Service : place\n@enduml") };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-wholetestflow.html", "Kronikol Run", includeTestRunData: false,
        internalFlowTracking: true, wholeTestSegments: wholeTestSegments,
        wholeTestVisualization: WholeTestFlowVisualization.Both);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-wholetestflow.html"), content);
    Console.WriteLine($"=== report-wholetestflow.html ({content.Length} chars) ===");
}

void DumpInternalFlowFlame()
{
    // A 300ms root so the percentages are fractional (33.33, 36.67, …) — exercises the System.Text.Json
    // double formatting + banker's rounding. Same tree shape as the activity dump.
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);

    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("OrderService");
    using var dbSrc = new ActivitySource("Database");

    var root = reqSrc.StartActivity("GET /orders")!;
    root.SetStartTime(t0);
    var load = svcSrc.StartActivity("LoadOrder")!;
    load.SetStartTime(t0.AddMilliseconds(100));
    var sel = dbSrc.StartActivity("SELECT")!;
    sel.SetStartTime(t0.AddMilliseconds(110));
    sel.SetEndTime(t0.AddMilliseconds(130));                // 20ms
    sel.Stop();
    load.SetEndTime(t0.AddMilliseconds(140));               // 40ms
    load.Stop();
    var val = svcSrc.StartActivity("Validate")!;
    val.SetStartTime(t0.AddMilliseconds(200));
    val.SetEndTime(t0.AddMilliseconds(230));                // 30ms
    val.Stop();
    root.SetEndTime(t0.AddMilliseconds(300));               // 300ms
    root.Stop();

    var segment = new InternalFlowSegment(
        Guid.Empty, RequestResponseType.Request, "t1",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(300), TimeSpan.Zero),
        new[] { root, load, sel, val });

    var boundaryLogs = new[]
    {
        ("GET: /orders", new DateTimeOffset(t0, TimeSpan.Zero)),
        // Special chars to capture System.Text.Json's default (HTML-safe) escaping exactly.
        ("a&b<c>\"d'e+f/g", new DateTimeOffset(t0.AddMilliseconds(60), TimeSpan.Zero)),
        ("DB: /query", new DateTimeOffset(t0.AddMilliseconds(110), TimeSpan.Zero))
    };
    var flameData = InternalFlowRenderer.GetFlameChartDataWithMarkers(segment, boundaryLogs);
    var flameJson = System.Text.Json.JsonSerializer.Serialize(
        flameData.Markers != null
            ? (object)new { s = flameData.Sources, f = flameData.Spans, m = flameData.Markers }
            : new { s = flameData.Sources, f = flameData.Spans },
        new System.Text.Json.JsonSerializerOptions { WriteIndented = false });
    File.WriteAllText(Path.Combine(outDir, "iflow-flame.json"), flameJson);
    Console.WriteLine($"=== iflow-flame.json ({flameJson.Length} chars) ===");
    Console.WriteLine(flameJson);
}

void CaptureHtmlPopup()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);
    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("OrderService");
    using var dbSrc = new ActivitySource("Database");
    var root = reqSrc.StartActivity("GET /orders")!; root.SetStartTime(t0);
    var load = svcSrc.StartActivity("LoadOrder")!; load.SetStartTime(t0.AddMilliseconds(10));
    var sel = dbSrc.StartActivity("SELECT")!; sel.SetStartTime(t0.AddMilliseconds(15)); sel.SetEndTime(t0.AddMilliseconds(35)); sel.Stop();
    load.SetEndTime(t0.AddMilliseconds(50)); load.Stop();
    var val = svcSrc.StartActivity("Validate")!; val.SetStartTime(t0.AddMilliseconds(60)); val.SetEndTime(t0.AddMilliseconds(90)); val.Stop();
    root.SetEndTime(t0.AddMilliseconds(100)); root.Stop();
    var rrid = Guid.Parse("00000000-0000-0000-0000-000000000001");
    var segment = new InternalFlowSegment(rrid, RequestResponseType.Request, "s1",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(100), TimeSpan.Zero),
        new[] { root, load, sel, val });
    var perDiagram = new Dictionary<string, InternalFlowSegment> { ["iflow-" + rrid] = segment };
    // CallTree style → the window.__iflowSegments script has no gzip and is wholly byte-comparable.
    var idScript = DiagramContextMenu.GetInternalFlowConfigScript(InternalFlowHasDataBehavior.ShowLinkOnHover)
        + InternalFlowHtmlGenerator.GenerateSegmentDataScript(perDiagram, InternalFlowDiagramStyle.CallTree,
            showFlameChart: true, InternalFlowFlameChartPosition.BehindWithToggle,
            InternalFlowNoDataBehavior.HideLink, InternalFlowSpanGranularity.AutoInstrumentation, null);

    var scenario = new Scenario { Id = "s1", DisplayName = "Order flow", IsHappyPath = true, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100) };
    var feature = new Feature { DisplayName = "Orders", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-popup.html", "Kronikol Run", includeTestRunData: false,
        internalFlowTracking: true, internalFlowDataScript: idScript);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-popup.html"), content);
    Console.WriteLine($"=== report-popup.html ({content.Length} chars) ===");
}

void DumpInternalFlowSegmentData()
{
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);
    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("OrderService");
    using var dbSrc = new ActivitySource("Database");
    var root = reqSrc.StartActivity("GET /orders")!; root.SetStartTime(t0);
    var load = svcSrc.StartActivity("LoadOrder")!; load.SetStartTime(t0.AddMilliseconds(10));
    var sel = dbSrc.StartActivity("SELECT")!; sel.SetStartTime(t0.AddMilliseconds(15)); sel.SetEndTime(t0.AddMilliseconds(35)); sel.Stop();
    load.SetEndTime(t0.AddMilliseconds(50)); load.Stop();
    var val = svcSrc.StartActivity("Validate")!; val.SetStartTime(t0.AddMilliseconds(60)); val.SetEndTime(t0.AddMilliseconds(90)); val.Stop();
    root.SetEndTime(t0.AddMilliseconds(100)); root.Stop();
    // Fixed RequestResponseId so the segment key is deterministic.
    var rrid = Guid.Parse("00000000-0000-0000-0000-000000000001");
    var segment = new InternalFlowSegment(rrid, RequestResponseType.Request, "t1",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(100), TimeSpan.Zero),
        new[] { root, load, sel, val });
    var segments = new Dictionary<string, InternalFlowSegment> { ["iflow-" + rrid] = segment };
    // CallTree style → no gzip data-plantuml-z, so the whole script is byte-comparable.
    var script = InternalFlowHtmlGenerator.GenerateSegmentDataScript(
        segments, InternalFlowDiagramStyle.CallTree, showFlameChart: true,
        InternalFlowFlameChartPosition.BehindWithToggle, InternalFlowNoDataBehavior.HideLink,
        InternalFlowSpanGranularity.AutoInstrumentation, null);
    File.WriteAllText(Path.Combine(outDir, "iflow-segmentdata.txt"), script);
    Console.WriteLine($"=== iflow-segmentdata.txt ({script.Length} chars) ===");
}

void DumpInternalFlowCallTree()
{
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);
    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("OrderService");
    using var dbSrc = new ActivitySource("Database");
    var root = reqSrc.StartActivity("GET /orders")!; root.SetStartTime(t0);
    var load = svcSrc.StartActivity("LoadOrder")!; load.SetStartTime(t0.AddMilliseconds(10));
    var sel = dbSrc.StartActivity("SELECT")!; sel.SetStartTime(t0.AddMilliseconds(15)); sel.SetEndTime(t0.AddMilliseconds(35)); sel.Stop();
    load.SetEndTime(t0.AddMilliseconds(50)); load.Stop();
    var val = svcSrc.StartActivity("Validate")!; val.SetStartTime(t0.AddMilliseconds(60)); val.SetEndTime(t0.AddMilliseconds(90)); val.Stop();
    root.SetEndTime(t0.AddMilliseconds(100)); root.Stop();
    var segment = new InternalFlowSegment(Guid.Empty, RequestResponseType.Request, "t1",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(100), TimeSpan.Zero),
        new[] { root, load, sel, val });

    var html = InternalFlowRenderer.RenderCallTree(segment);
    File.WriteAllText(Path.Combine(outDir, "iflow-calltree.txt"), html);   // native CRLF
    Console.WriteLine($"=== iflow-calltree.txt ({html.Length} chars, crlf={html.Contains("\r\n")}) ===");
}

void DumpInternalFlowActivity()
{
    // A deterministic span tree: GET /orders (root) → LoadOrder → SELECT, then Validate (sibling).
    using var listener = new ActivityListener
    {
        ShouldListenTo = _ => true,
        Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        SampleUsingParentId = (ref ActivityCreationOptions<string> _) => ActivitySamplingResult.AllData
    };
    ActivitySource.AddActivityListener(listener);

    var t0 = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    using var reqSrc = new ActivitySource("Kronikol.Request");
    using var svcSrc = new ActivitySource("OrderService");
    using var dbSrc = new ActivitySource("Database");

    var root = reqSrc.StartActivity("GET /orders")!;
    root.SetStartTime(t0);
    var load = svcSrc.StartActivity("LoadOrder")!;          // parent = root
    load.SetStartTime(t0.AddMilliseconds(10));
    var sel = dbSrc.StartActivity("SELECT")!;               // parent = load
    sel.SetStartTime(t0.AddMilliseconds(15));
    sel.SetEndTime(t0.AddMilliseconds(35));                 // 20ms
    sel.Stop();
    load.SetEndTime(t0.AddMilliseconds(50));                // 40ms
    load.Stop();
    var val = svcSrc.StartActivity("Validate")!;            // parent = root (Current back to root)
    val.SetStartTime(t0.AddMilliseconds(60));
    val.SetEndTime(t0.AddMilliseconds(90));                 // 30ms
    val.Stop();
    root.SetEndTime(t0.AddMilliseconds(100));               // 100ms
    root.Stop();

    var segment = new InternalFlowSegment(
        Guid.Empty, RequestResponseType.Request, "t1",
        new DateTimeOffset(t0, TimeSpan.Zero), new DateTimeOffset(t0.AddMilliseconds(100), TimeSpan.Zero),
        new[] { root, load, sel, val });

    var batches = InternalFlowRenderer.RenderActivityDiagramBatched(segment, 100);
    // Write raw (no ReplaceLineEndings) so the fixture keeps the native CRLF the report gzips.
    File.WriteAllText(Path.Combine(outDir, "iflow-activity.txt"), batches[0]);
    var crlf = batches[0].Contains("\r\n");
    Console.WriteLine($"=== iflow-activity.txt ({batches[0].Length} chars, batches={batches.Length}, crlf={crlf}) ===");
}

void CaptureHtmlDiagramToggles()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // A diagram whose PlantUML carries all three markers → hasAssertionNotes / hasStepDelimiters /
    // hasDatabaseParticipants all true → the Assertions/Steps/Databases toggle buttons render in both
    // the global toolbar (_toggle*) and the scenario diagram bar (_toggleScenario*).
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Saves order", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100),
        Steps = [ new ScenarioStep { Keyword = "When", Text = "the order is saved", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20) } ]
    };
    var feature = new Feature { DisplayName = "Orders", Scenarios = [scenario] };
    var puml = "@startuml\ndatabase \"OrderDB\" as db\nactor User\nUser -> db : <<stepDelimiter>> save\nnote over db <<assertionNote>> : verify\n@enduml";
    var diagrams = new[] { new DefaultDiagramsFetcher.DiagramAsCode("s1", "", puml) };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-diagramtoggles.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-diagramtoggles.html"), content);
    Console.WriteLine($"=== report-diagramtoggles.html ({content.Length} chars) ===");
}

void CaptureHtmlStepNumbers()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // showStepNumbers=true → background steps numbered 1., 2.…; scenario steps 1., 2., 3.; sub-steps 1.1.
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Places an order", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100),
        BackgroundSteps =
        [
            new ScenarioStep { Keyword = "Given", Text = "the database is seeded", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(5) }
        ],
        Steps =
        [
            new ScenarioStep
            {
                Keyword = "Given", Text = "a logged-in user", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10),
                SubSteps = [ new ScenarioStep { Keyword = "And", Text = "a valid session", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(2) } ]
            },
            new ScenarioStep { Keyword = "When", Text = "the order is placed", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20) },
            new ScenarioStep { Keyword = "Then", Text = "it is confirmed", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(15) }
        ]
    };
    var feature = new Feature { DisplayName = "Orders", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-stepnumbers.html", "Kronikol Run", includeTestRunData: false,
        showStepNumbers: true);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-stepnumbers.html"), content);
    Console.WriteLine($"=== report-stepnumbers.html ({content.Length} chars) ===");
}

void CaptureHtmlBlankOnFail()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // generateBlankOnFailedTests=true with a failed scenario → an empty report file.
    var scenario = new Scenario { Id = "s1", DisplayName = "Fails", Result = ExecutionResult.Failed, ErrorMessage = "boom" };
    var feature = new Feature { DisplayName = "F", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-blankonfail.html", "Kronikol Run", includeTestRunData: false,
        generateBlankOnFailedTests: true);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-blankonfail.html"), content);
    Console.WriteLine($"=== report-blankonfail.html ({content.Length} chars) ===");
}

void CaptureHtmlCustomAssets()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // customCss → <style> after the main stylesheet; customFaviconBase64 → the favicon href;
    // customLogoHtml → a custom-logo div above the <h1>.
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Loads home page", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(40),
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "the page renders", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var feature = new Feature { DisplayName = "Web", Scenarios = [s1] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-customassets.html", "Kronikol Run", includeTestRunData: false,
        customCss: ".kx-banner { color: #c0ffee; }",
        customFaviconBase64: "data:image/png;base64,AAAA",
        customLogoHtml: "<img src=\"logo.png\" alt=\"Acme\">");
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-customassets.html"), content);
    Console.WriteLine($"=== report-customassets.html ({content.Length} chars) ===");
}

void CaptureHtmlCustomStyleSheet()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // The 5th positional arg (stylesheet) is appended into the main <style> after the base sheet, and is
    // distinct from customCss (a separate trailing <style>): pass both to prove the placement difference.
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Renders the spec", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(40),
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "the spec renders", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var feature = new Feature { DisplayName = "Specs", Scenarios = [s1] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, ".kx-spec { margin: 0 }\n.kx-spec h2 { color: #336699 }",
        "report-customstylesheet.html", "Kronikol Run", includeTestRunData: false,
        customCss: ".kx-after { color: #c0ffee; }");
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-customstylesheet.html"), content);
    Console.WriteLine($"=== report-customstylesheet.html ({content.Length} chars) ===");
}

void CaptureCiSummaryFailed()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc); // 5s
    // A mixed run with no diagrams: the metrics table + ## Failed Scenarios details (error with a pipe to
    // exercise EscapeMarkdown, a name with < & to exercise EscapeHtml, a stack trace) — all byte-stable.
    var passedS = new Scenario { Id = "s0", DisplayName = "Loads home", IsHappyPath = true, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(40) };
    var failed1 = new Scenario { Id = "s1", DisplayName = "Login <fails> & burns", Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(50), ErrorMessage = "expected 200 | got 500", ErrorStackTrace = "at Login.Do()\n  at Test.Run()" };
    var failed2 = new Scenario { Id = "s2", DisplayName = "Checkout fails", Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(20), ErrorMessage = "boom" };
    var skippedS = new Scenario { Id = "s3", DisplayName = "Deferred", Result = ExecutionResult.Skipped, Duration = TimeSpan.Zero };
    var feature = new Feature { DisplayName = "Web", Scenarios = [passedS, failed1, failed2, skippedS] };
    var none = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var md = CiSummaryGenerator.GenerateMarkdown([feature], none, none, start, end).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "ci-summary-failed.md"), md);
    Console.WriteLine($"=== ci-summary-failed.md ({md.Length} chars) ===");
}

void CaptureCiSummaryMultipart()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 1, 5, DateTimeKind.Utc);
    // truncated != full (truncation occurred) AND 2 parts per scenario → the wasTruncated + multipart
    // branches: "Truncated/Full Sequence Diagram (Part N)" + the "(Part N) - PlantUML" source blocks.
    var s1 = new Scenario { Id = "s1", DisplayName = "Places an order", IsHappyPath = true, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(120) };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [s1] };
    var truncated = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", string.Empty, "@startuml\nTest -> A: part1 short\n@enduml"),
        new DefaultDiagramsFetcher.DiagramAsCode("s1", string.Empty, "@startuml\nTest -> B: part2 short\n@enduml"),
    };
    var full = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", string.Empty, "@startuml\nTest -> A: part1 full body content\n@enduml"),
        new DefaultDiagramsFetcher.DiagramAsCode("s1", string.Empty, "@startuml\nTest -> B: part2 full body content\n@enduml"),
    };
    var md = CiSummaryGenerator.GenerateMarkdown([feature], truncated, full, start, end).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "ci-summary-multipart.md"), md);
    Console.WriteLine($"=== ci-summary-multipart.md ({md.Length} chars) ===");
}

void CaptureCiSummaryDiagrams()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 1, 5, DateTimeKind.Utc); // 1m 5s
    // An all-passed run with one (un-truncated) diagram per scenario → ## Sequence Diagrams with the
    // ![diagram](server/svg/<encoded>) link + the ```plantuml source block. The <encoded> DEFLATE token is
    // masked in the Java compare; the source block + structure are byte-stable.
    var s1 = new Scenario { Id = "s1", DisplayName = "Places an order", IsHappyPath = true, Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(120) };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [s1] };
    var puml = "@startuml\nTest -> orderService: POST: http://svc/orders\norderService --> Test: 201\n@enduml";
    var diagrams = new[] { new DefaultDiagramsFetcher.DiagramAsCode("s1", string.Empty, puml) };
    var md = CiSummaryGenerator.GenerateMarkdown([feature], diagrams, diagrams, start, end).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "ci-summary-diagrams.md"), md);
    Console.WriteLine($"=== ci-summary-diagrams.md ({md.Length} chars) ===");
}

void CaptureDiagnosticReport()
{
    // Logs exercising every deterministic section: paired (s1/s2), unpaired (PaymentService), "unknown"
    // entries (BackgroundSvc x2 → breakdown with first/last seen), an orphaned test id (orphan1), plus
    // `&` / `<` in names and a `&` in a URI to prove WebUtility.HtmlEncode escaping.
    Guid Rr(int n) => Guid.Parse($"00000000-0000-0000-0000-0000000001{n:x2}");
    Guid Tr(int n) => Guid.Parse($"00000000-0000-0000-0000-0000000002{n:x2}");
    DateTimeOffset Ts(int s, int ms = 0) => new DateTimeOffset(2024, 1, 15, 10, 0, s, ms, TimeSpan.Zero);
    var logs = new[]
    {
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{}", new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test", RequestResponseType.Request, Tr(1), Rr(1), false) { Timestamp = Ts(1) },
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{}", new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test", RequestResponseType.Response, Tr(1), Rr(1), false, StatusCode: HttpStatusCode.OK) { Timestamp = Ts(1, 250) },
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{}", new Uri("http://pay/charge?amt=5&cur=USD"), NoHeaders(), "PaymentService", "Test", RequestResponseType.Request, Tr(2), Rr(2), false) { Timestamp = Ts(1, 300) },
        new RequestResponseLog("Lookup order", "s2", HttpMethod.Get, null, new Uri("http://orders/123"), NoHeaders(), "OrderService", "Test", RequestResponseType.Request, Tr(3), Rr(3), false) { Timestamp = Ts(2) },
        new RequestResponseLog("Lookup order", "s2", HttpMethod.Get, null, new Uri("http://orders/123"), NoHeaders(), "OrderService", "Test", RequestResponseType.Response, Tr(3), Rr(3), false, StatusCode: HttpStatusCode.OK) { Timestamp = Ts(2, 100) },
        new RequestResponseLog("BackgroundPoll", "unknown", HttpMethod.Get, null, new Uri("http://bg/poll"), NoHeaders(), "BackgroundSvc", "Test", RequestResponseType.Request, Tr(4), Rr(4), false) { Timestamp = Ts(2) },
        new RequestResponseLog("BackgroundPoll", "unknown", HttpMethod.Get, null, new Uri("http://bg/poll"), NoHeaders(), "BackgroundSvc", "Test", RequestResponseType.Request, Tr(5), Rr(5), false) { Timestamp = Ts(8) },
        new RequestResponseLog("Ghost & Co", "orphan1", HttpMethod.Get, null, new Uri("http://ghost/x"), NoHeaders(), "GhostService", "Test", RequestResponseType.Request, Tr(6), Rr(6), false) { Timestamp = Ts(3) },
    };
    var checkout = new Feature { DisplayName = "Checkout", Scenarios = [
        new Scenario { Id = "s1", DisplayName = "Checkout succeeds", Result = ExecutionResult.Passed },
        new Scenario { Id = "s3", DisplayName = "Edge <case>", Result = ExecutionResult.Passed } ] };
    var lookup = new Feature { DisplayName = "Lookup", Scenarios = [
        new Scenario { Id = "s2", DisplayName = "Lookup order", Result = ExecutionResult.Passed } ] };
    var options = new ReportConfigurationOptions { ReportsFolderPath = "diag-out" };
    // BuildHtml is internal; use the public Generate (writes DiagnosticReport.html) and read it back.
    DiagnosticReportGenerator.Generate(logs, [checkout, lookup], options);
    var generated = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "diag-out", "DiagnosticReport.html");
    var html = File.ReadAllText(generated).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "diagnostic-report.html"), html);
    Console.WriteLine($"=== diagnostic-report.html ({html.Length} chars) ===");
}

void CaptureReportDataSchema()
{
    var path = ReportGenerator.GenerateTestRunReportSchema("testrunreport.schema.json", DataFormat.Json);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "testrunreport-schema.json"), content);
    Console.WriteLine($"=== testrunreport-schema.json ({content.Length} chars) ===");

    var xsdPath = ReportGenerator.GenerateTestRunReportSchema("testrunreport.schema.xsd", DataFormat.Xml);
    var xsd = File.ReadAllText(xsdPath).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "testrunreport-schema.xsd"), xsd);
    Console.WriteLine($"=== testrunreport-schema.xsd ({xsd.Length} chars) ===");
}

void CaptureReportDiagnostics()
{
    // logs: a paired pair (s1) + an unpaired request (s1) + an orphaned-test-id request (orphan1) → the
    // log-count, unpaired, orphaned and 0-spans warnings. InternalFlowSpanStore is empty in the harness.
    Guid Rr(int n) => Guid.Parse($"00000000-0000-0000-0000-0000000001{n:x2}");
    var logs = new[]
    {
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{}", new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test", RequestResponseType.Request, Rr(10), Rr(1), false),
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{}", new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test", RequestResponseType.Response, Rr(10), Rr(1), false),
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{}", new Uri("http://pay/charge"), NoHeaders(), "PaymentService", "Test", RequestResponseType.Request, Rr(11), Rr(2), false),
        new RequestResponseLog("Ghost", "orphan1", HttpMethod.Get, null, new Uri("http://ghost/x"), NoHeaders(), "GhostService", "Test", RequestResponseType.Request, Rr(12), Rr(3), false),
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [
        new Scenario { Id = "s1", DisplayName = "Checkout succeeds", Result = ExecutionResult.Passed } ] };
    var warnings = ReportDiagnostics.Analyse(logs, [feature]);
    var text = string.Join("\n", warnings);
    File.WriteAllText(Path.Combine(outDir, "report-diagnostics.txt"), text);
    Console.WriteLine($"=== report-diagnostics.txt ({warnings.Length} warning(s)) ===");
}

void CaptureHtmlCiMetadata()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // includeTestRunData=true + ciMetadata → the ci-chart-group wrapping the ci-metadata table + pie.
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Login succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100),
        Steps = [ new ScenarioStep { Keyword = "When", Text = "the user logs in", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30) } ]
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Login fails", IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(50), ErrorMessage = "bad password"
    };
    var feature = new Feature { DisplayName = "Login", Scenarios = [s1, s2] };
    var ci = new CiMetadata(
        CiEnvironment.GitHubActions, "42", "main", "abc1234def5678",
        "https://github.com/acme/repo/actions/runs/99", "acme/repo", "99");
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-cimetadata.html", "Kronikol Run", includeTestRunData: true,
        ciMetadata: ci);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-cimetadata.html"), content);
    Console.WriteLine($"=== report-cimetadata.html ({content.Length} chars) ===");
}

void CaptureHtmlSummary()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var login1 = new Scenario
    {
        Id = "s1", DisplayName = "Login succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(100),
        Steps = [ new ScenarioStep { Keyword = "When", Text = "the user logs in", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30) } ]
    };
    var login2 = new Scenario
    {
        Id = "s2", DisplayName = "Login fails", IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(50),
        ErrorMessage = "bad password"
    };
    var loginFeature = new Feature { DisplayName = "Login", Scenarios = [login1, login2] };
    var checkout1 = new Scenario
    {
        Id = "s3", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(200)
    };
    var checkoutFeature = new Feature { DisplayName = "Checkout", Scenarios = [checkout1] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [loginFeature, checkoutFeature], start, end, null, "report-summary.html", "Kronikol Run", includeTestRunData: true);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-summary.html"), content);
    Console.WriteLine($"=== report-summary.html ({content.Length} chars) ===");
}

void CaptureHtmlStepDetails()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Steps =
        [
            new ScenarioStep
            {
                Keyword = "When", Text = "the user submits the order", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(40),
                Comments = [ "verify the payload", "idempotency key sent" ],
                DocString = "{\n  \"id\": 42,\n  \"total\": \"9.99\"\n}", DocStringMediaType = "json"
            }
        ]
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-stepdetails.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-stepdetails.html"), content);
    Console.WriteLine($"=== report-stepdetails.html ({content.Length} chars) ===");
}

void CaptureHtmlStepSegments()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Steps =
        [
            new ScenarioStep
            {
                Keyword = "Then", Text = "paid 9.99 with code 200", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(25),
                TextSegments =
                [
                    StepTextSegment.Literal("paid "),
                    StepTextSegment.Param("amount", new InlineParameterValue("9.99", null, VerificationStatus.NotApplicable)),
                    StepTextSegment.Literal(" with code "),
                    StepTextSegment.Param("code", new InlineParameterValue("200", "200", VerificationStatus.Success))
                ]
            }
        ]
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-stepsegments.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-stepsegments.html"), content);
    Console.WriteLine($"=== report-stepsegments.html ({content.Length} chars) ===");
}

void CaptureHtmlStepParams()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Checkout succeeds", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Steps =
        [
            new ScenarioStep
            {
                Keyword = "When", Text = "the amount is charged", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(15),
                Parameters =
                [
                    new StepParameter { Name = "amount", Kind = StepParameterKind.Inline, InlineValue = new InlineParameterValue("9.99", null, VerificationStatus.NotApplicable) }
                ]
            }
        ]
    };
    var feature = new Feature { DisplayName = "Checkout", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-stepparams.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-stepparams.html"), content);
    Console.WriteLine($"=== report-stepparams.html ({content.Length} chars) ===");
}

void CaptureHtmlStepTables()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var tabular = new StepParameter
    {
        Name = "items", Kind = StepParameterKind.Tabular,
        TabularValue = new TabularParameterValue(
            [ new TabularColumn("name", true), new TabularColumn("qty", false) ],
            [
                new TabularRow(TableRowType.Matching, [ new TabularCell("egg", null, VerificationStatus.NotApplicable), new TabularCell("2", null, VerificationStatus.NotApplicable) ]),
                new TabularRow(TableRowType.Surplus, [ new TabularCell("bonus", null, VerificationStatus.NotApplicable), new TabularCell("1", null, VerificationStatus.NotApplicable) ])
            ])
    };
    var tree = new StepParameter
    {
        Name = "config", Kind = StepParameterKind.Tree,
        TreeValue = new TreeParameterValue(new TreeNode("", "root", "", null, VerificationStatus.NotApplicable,
        [
            new TreeNode("root.a", "a", "1", null, VerificationStatus.Success, null),
            new TreeNode("root.b", "b", "2", "3", VerificationStatus.Failure, null)
        ]))
    };
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Data is processed", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Steps =
        [
            new ScenarioStep { Keyword = "When", Text = "the cart is loaded", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20), Parameters = [ tabular ] },
            new ScenarioStep { Keyword = "Then", Text = "the config matches", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30), Parameters = [ tree ] }
        ]
    };
    var feature = new Feature { DisplayName = "Data", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-steptables.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-steptables.html"), content);
    Console.WriteLine($"=== report-steptables.html ({content.Length} chars) ===");
}

void CaptureHtmlCombinedTable()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var setup = new StepParameter
    {
        Name = "inputs", Kind = StepParameterKind.Tabular,
        TabularValue = new TabularParameterValue(
            [ new TabularColumn("id", true), new TabularColumn("name", false) ],
            [
                new TabularRow(TableRowType.Matching, [ new TabularCell("1", null, VerificationStatus.NotApplicable), new TabularCell("egg", null, VerificationStatus.NotApplicable) ]),
                new TabularRow(TableRowType.Matching, [ new TabularCell("2", null, VerificationStatus.NotApplicable), new TabularCell("milk", null, VerificationStatus.NotApplicable) ])
            ])
    };
    var assertion = new StepParameter
    {
        Name = "outputs", Kind = StepParameterKind.Tabular,
        TabularValue = new TabularParameterValue(
            [ new TabularColumn("id", true), new TabularColumn("total", false) ],
            [
                new TabularRow(TableRowType.Matching, [ new TabularCell("1", null, VerificationStatus.Success), new TabularCell("9.99", null, VerificationStatus.Success) ]),
                new TabularRow(TableRowType.Matching, [ new TabularCell("2", null, VerificationStatus.Success), new TabularCell("4.99", null, VerificationStatus.Success) ])
            ])
    };
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Cart totals", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Steps =
        [
            new ScenarioStep { Keyword = "Given", Text = "the cart is set up", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20), Parameters = [ setup ] },
            new ScenarioStep { Keyword = "Then", Text = "the totals are correct", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30), Parameters = [ assertion ] }
        ]
    };
    var feature = new Feature { DisplayName = "Cart", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-combinedtable.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-combinedtable.html"), content);
    Console.WriteLine($"=== report-combinedtable.html ({content.Length} chars) ===");
}

void CaptureHtmlComplexParams()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    var scenario = new Scenario
    {
        Id = "s1", DisplayName = "Order is placed", IsHappyPath = true,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(1500),
        Steps =
        [
            new ScenarioStep
            {
                Keyword = "Then", Text = "small and large", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(25),
                TextSegments =
                [
                    StepTextSegment.Literal("small "),
                    StepTextSegment.TableRef("item"),
                    StepTextSegment.Literal(" large "),
                    StepTextSegment.TableRef("config")
                ],
                Parameters =
                [
                    new StepParameter { Name = "item", Kind = StepParameterKind.Inline, InlineValue = new InlineParameterValue("Item { Id = 5, Name = egg }", null, VerificationStatus.NotApplicable) },
                    new StepParameter { Name = "config", Kind = StepParameterKind.Inline, InlineValue = new InlineParameterValue("Config { A = 1, B = 2, C = 3, D = 4, E = 5 }", null, VerificationStatus.NotApplicable) }
                ]
            }
        ]
    };
    var feature = new Feature { DisplayName = "Orders", Scenarios = [scenario] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-complexparams.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-complexparams.html"), content);
    Console.WriteLine($"=== report-complexparams.html ({content.Length} chars) ===");
}

void CaptureHtmlParamComplexCells()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // Two examples sharing OutlineId "Recipes" with five scalar-keyed ExampleValues whose strings are
    // .NET record ToString() shapes. ExampleRawValues is NOT set, so each cell takes the string-based
    // path: small (R3 cell-subtable), big (R4 param-expand details), nested record (recursion),
    // collection type name (cleaned to List<Item>), and a plain scalar (falls to <td class="mono">).
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Bakes a cake", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50),
        OutlineId = "Recipes",
        ExampleValues = new()
        {
            ["small"] = "Item { Id = 5, Name = egg, Price = 3 }",
            ["big"] = "Config { A = 1, B = 2, C = 3, D = 4, E = 5, F = 6 }",
            ["nested"] = "Order { Id = 1, Who = Person { Name = Bob, Age = 30 } }",
            ["coll"] = "Cart { Items = System.Collections.Generic.List`1[MyApp.Models.Item], Total = 10 }",
            ["plain"] = "42"
        },
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "it bakes", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Bakes bread", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60),
        OutlineId = "Recipes",
        ExampleValues = new()
        {
            ["small"] = "Item { Id = 7, Name = ham }",
            ["big"] = "Config { A = 9, B = 8, C = 7, D = 6, E = 5, F = 4, G = 3 }",
            ["nested"] = "Order { Id = 2, Who = Person { Name = Sue, Age = 25 } }",
            ["coll"] = "Cart { Items = System.Collections.Generic.List`1[MyApp.Models.Item], Total = 20 }",
            ["plain"] = "99"
        },
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "it bakes", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var feature = new Feature { DisplayName = "Recipes", Scenarios = [s1, s2] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-paramcells.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-paramcells.html"), content);
    Console.WriteLine($"=== report-paramcells.html ({content.Length} chars) ===");
}

void CaptureHtmlR2Flatten()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // Two examples sharing OutlineId "Orders" with a SINGLE param "order" whose value is a record
    // ToString() string. With no ExampleRawValues, ParameterGrouper.TryStringBasedFlatten parses it
    // and flattens to one column per property (R2 FlattenedObject) — Id/Total scalar, Who a nested
    // record rendered as an R3 cell-subtable in each cell.
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Order one", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50),
        OutlineId = "Orders",
        ExampleValues = new() { ["order"] = "Order { Id = 1, Who = Person { Name = Bob, Age = 30 }, Total = 50 }" },
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "it ships", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Order two", IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(60),
        OutlineId = "Orders",
        ExampleValues = new() { ["order"] = "Order { Id = 2, Who = Person { Name = Sue, Age = 25 }, Total = 75 }" },
        ErrorMessage = "Expected: shipped\nActual: pending"
    };
    var feature = new Feature { DisplayName = "Fulfilment", Scenarios = [s1, s2] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-r2flatten.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-r2flatten.html"), content);
    Console.WriteLine($"=== report-r2flatten.html ({content.Length} chars) ===");
}

void CaptureHtmlFlattenToggle()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // Each example carries BOTH structured ExampleValues (the grouped ScalarColumns view: name/age)
    // AND original ExampleFlatValues (the flat view: a single "user" column). The report shows the
    // flat table (param-table-flat, visible) with a − toggle and the grouped table (param-table-grouped,
    // display:none) with a + toggle, wrapped in param-table-wrapper.
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Signup Bob", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50),
        OutlineId = "Signup",
        ExampleValues = new() { ["name"] = "Bob", ["age"] = "30" },
        ExampleFlatValues = new() { ["user"] = "Bob (30)" },
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "account exists", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Signup Sue", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(60),
        OutlineId = "Signup",
        ExampleValues = new() { ["name"] = "Sue", ["age"] = "25" },
        ExampleFlatValues = new() { ["user"] = "Sue (25)" },
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "account exists", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var feature = new Feature { DisplayName = "Accounts", Scenarios = [s1, s2] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-flattentoggle.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-flattentoggle.html"), content);
    Console.WriteLine($"=== report-flattentoggle.html ({content.Length} chars) ===");
}

void CaptureHtmlPrefixGroup()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    // No OutlineId, no ExampleValues — params are encoded in the display name. ParameterGrouper groups
    // by display-name prefix (ExtractBaseName "Login") and DetermineParamsAndRule parses the names
    // (ParameterParser.Parse) into user/role columns (ScalarColumns).
    var s1 = new Scenario
    {
        Id = "s1", DisplayName = "Login(user: bob, role: admin)", IsHappyPath = false,
        Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(50),
        Steps = [ new ScenarioStep { Keyword = "Then", Text = "access granted", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) } ]
    };
    var s2 = new Scenario
    {
        Id = "s2", DisplayName = "Login(user: sue, role: guest)", IsHappyPath = false,
        Result = ExecutionResult.Failed, Duration = TimeSpan.FromMilliseconds(60),
        ErrorMessage = "Expected: granted\nActual: denied"
    };
    var feature = new Feature { DisplayName = "Security", Scenarios = [s1, s2] };
    var diagrams = Array.Empty<DefaultDiagramsFetcher.DiagramAsCode>();
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-prefixgroup.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-prefixgroup.html"), content);
    Console.WriteLine($"=== report-prefixgroup.html ({content.Length} chars) ===");
}

void CaptureHtmlParamDiagrams()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);
    const string d1 = "@startuml\nA -> B : one\n@enduml";
    const string d2 = "@startuml\nA -> B : two\n@enduml";
    const string d3 = "@startuml\nA -> B : three\n@enduml";
    // Group "Same": both examples share diagram d1 → one shared diagram + identical badge.
    var s1 = new Scenario { Id = "s1", DisplayName = "Same A", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10), OutlineId = "Same", ExampleValues = new() { ["n"] = "1" } };
    var s2 = new Scenario { Id = "s2", DisplayName = "Same B", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20), OutlineId = "Same", ExampleValues = new() { ["n"] = "2" } };
    // Group "Diff": each example has a different diagram → per-example diagrams (first shown).
    var s3 = new Scenario { Id = "s3", DisplayName = "Diff A", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30), OutlineId = "Diff", ExampleValues = new() { ["n"] = "3" } };
    var s4 = new Scenario { Id = "s4", DisplayName = "Diff B", Result = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(40), OutlineId = "Diff", ExampleValues = new() { ["n"] = "4" } };
    var feature = new Feature { DisplayName = "Flows", Scenarios = [s1, s2, s3, s4] };
    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "", d1),
        new DefaultDiagramsFetcher.DiagramAsCode("s2", "", d1),
        new DefaultDiagramsFetcher.DiagramAsCode("s3", "", d2),
        new DefaultDiagramsFetcher.DiagramAsCode("s4", "", d3)
    };
    var path = ReportGenerator.GenerateHtmlReport(
        diagrams, [feature], start, end, null, "report-paramdiagrams.html", "Kronikol Run", includeTestRunData: false);
    var content = File.ReadAllText(path).ReplaceLineEndings("\n");
    File.WriteAllText(Path.Combine(outDir, "report-paramdiagrams.html"), content);
    Console.WriteLine($"=== report-paramdiagrams.html ({content.Length} chars) ===");
}

void CaptureReportData()
{
    var start = new DateTime(2024, 1, 15, 10, 0, 0, DateTimeKind.Utc);
    var end = new DateTime(2024, 1, 15, 10, 0, 5, DateTimeKind.Utc);

    var s1 = new Scenario
    {
        Id = "s1",
        DisplayName = "Checkout succeeds",
        IsHappyPath = true,
        Result = ExecutionResult.Passed,
        Duration = TimeSpan.FromMilliseconds(1500),
        Labels = ["fast"],
        Categories = ["api"],
        Rule = "Happy path",
        BackgroundSteps =
        [
            new ScenarioStep { Keyword = "Given", Text = "a logged-in user", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(10) }
        ],
        Steps =
        [
            new ScenarioStep { Keyword = "Given", Text = "an empty cart", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(20) },
            new ScenarioStep
            {
                Keyword = "When", Text = "the user checks out & pays", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(500),
                SubSteps = [ new ScenarioStep { Text = "POST /checkout", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(400) } ]
            },
            new ScenarioStep
            {
                Keyword = "Then", Text = "the order is confirmed", Status = ExecutionResult.Passed, Duration = TimeSpan.FromMilliseconds(30),
                Attachments = [ new FileAttachment("receipt.pdf", "attachments/receipt.pdf") ]
            }
        ],
        Attachments = [ new FileAttachment("trace.log", "attachments/trace.log") ]
    };

    var s2 = new Scenario
    {
        Id = "s2",
        DisplayName = "Checkout rejects empty cart",
        IsHappyPath = false,
        Result = ExecutionResult.Failed,
        Duration = TimeSpan.FromMilliseconds(12),
        ErrorMessage = "Expected <400> but got <500> & failed",
        ErrorStackTrace = "at Checkout.Validate()\n  at Checkout.Run()",
        OutlineId = "outline-1",
        ExampleValues = new() { ["cart"] = "empty", ["expected"] = "400" },
        ExampleDisplayName = "Empty cart"
    };

    var feature = new Feature
    {
        DisplayName = "Checkout",
        Endpoint = "/checkout",
        Description = "The checkout flow",
        Labels = ["smoke", "critical"],
        Scenarios = [s1, s2]
    };

    var (trace, rr) = Ids(7);
    var logs = new[]
    {
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{\"item\":\"egg\"}",
            new Uri("http://orders/checkout"), [("Accept", "application/json")], "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP")
            { Timestamp = new DateTimeOffset(2024, 1, 15, 10, 0, 1, 234, TimeSpan.Zero) },
        new RequestResponseLog("Checkout succeeds", "s1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Response, trace, rr, false, StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP")
            { Timestamp = new DateTimeOffset(2024, 1, 15, 10, 0, 1, 250, TimeSpan.Zero) }
    };

    var diagrams = new[]
    {
        new DefaultDiagramsFetcher.DiagramAsCode("s1", "", "@startuml\nactor Test\nTest -> OrderService\n@enduml")
    };

    foreach (var (fmt, ext) in new[] { (DataFormat.Json, "json"), (DataFormat.Xml, "xml"), (DataFormat.Yaml, "yaml") })
    {
        var path = ReportGenerator.GenerateTestRunReportData(
            [feature], start, end, $"report-data.{ext}", fmt, diagrams, logs);
        var content = File.ReadAllText(path).ReplaceLineEndings("\n");
        File.WriteAllText(Path.Combine(outDir, $"report-data.{ext}"), content);
        Console.WriteLine($"=== report-data.{ext} ({content.Length} chars) ===");
    }
}

void CaptureComponent(string name, List<RequestResponseLog> logs)
{
    var rels = ComponentDiagramGenerator.ExtractRelationships(logs);
    var puml = ComponentDiagramGenerator.GeneratePlantUml(rels, new ComponentDiagramOptions(), useC4: false);
    File.WriteAllText(Path.Combine(outDir, $"{name}.puml"), puml.ReplaceLineEndings("\n"));
    Console.WriteLine($"=== {name} ({puml.Length} chars) ===");
    Console.WriteLine(puml);
    Console.WriteLine();
}

Capture("simple-http", SimpleHttp(), arrowColors: false);
Capture("simple-http-colored", SimpleHttp(), arrowColors: true);
Capture("theme", SimpleHttp(), arrowColors: false, plantUmlTheme: "cyborg"); // !theme directive after @startuml
Capture("headers", HttpWithHeaders(), arrowColors: false); // gray [Key=Value] notes, sorted, Cache-Control excluded
Capture("setup", SetupCorpus(), arrowColors: false, separateSetup: true); // partition #F6F6F6 Setup … end
Capture("focus", FocusCorpus(), arrowColors: false); // focusFields → <b> focused, <color:lightgray> the rest
Capture("binary-content", BinaryContent(), arrowColors: false); // >10% control chars → [binary content]
Capture("form-encoded", FormEncoded(), arrowColors: false);     // non-JSON request body → form-url-encoded
Capture("long-url", LongUrl(), arrowColors: false);             // path+query > 100 chars → wrapped
Capture("note-on-right", NoteOnRightCorpus(), arrowColors: false); // request NoteOnRight → note on the right
Capture("multi-trace", MultiTrace(), arrowColors: false);
Capture("sql", Sql(), arrowColors: false);
Capture("event", Event(), arrowColors: false);
// Strengthened corpus: every dependency-type shape + arrow colour, status-code Titleize/redirect,
// PlantUML/JSON escaping, and single-trace fan-out participant ordering.
Capture("redis", Redis(), arrowColors: true);             // cache -> collections shape, #F39C12
Capture("storage", Storage(), arrowColors: true);         // S3 -> storage (database shape), #2ECC71
Capture("unknown-category", Unknown(), arrowColors: true); // unknown -> participant, #95A5A6
Capture("status-codes", StatusCodes(), arrowColors: false); // 404 / 500 / 302 label rendering
Capture("escaping", Escaping(), arrowColors: false);       // backslash + <>&" + unicode + nested JSON
Capture("fan-out", FanOut(), arrowColors: true);           // 3 services, one trace: participant order
// Participant-colour mode: the colour is appended to each categorised participant declaration.
Capture("participant-colors", SimpleHttp(), arrowColors: false, participantColors: true);
Capture("participant-colors-fanout", FanOut(), arrowColors: true, participantColors: true);
// GraphQL: op-label on the arrow + formatted query body. Default (FormattedWithMetadata) shows headers
// + variables (special chars HTML-escaped); FormattedQueryOnly suppresses headers + metadata; Json keeps
// the raw query string (no GraphQL formatting). The mutation corpus exercises the operationName override.
Capture("graphql", GraphQlQuery(), arrowColors: false);
Capture("graphql-query-only", GraphQlQuery(), arrowColors: false,
    graphQlBodyFormat: GraphQlBodyFormat.FormattedQueryOnly);
Capture("graphql-json", GraphQlQuery(), arrowColors: false, graphQlBodyFormat: GraphQlBodyFormat.Json);
Capture("graphql-mutation", GraphQlMutation(), arrowColors: false);
Capture("graphql-complex", GraphQlComplex(), arrowColors: false); // fragments, spreads, directives, aliases
CaptureGraphQlLabels(); // GraphQlOperationDetector.TryExtractLabel branch coverage (text fixture)
CaptureHtmlEscapeSamples(); // pin WebUtility.HtmlEncode vs the Java HtmlEscaper (text fixture)
// Internal-flow tracking wraps each request label in a clickable [[#iflow-<requestResponseId> …]] link.
Capture("internal-flow", SimpleHttp(), arrowColors: false, internalFlowTracking: true);
// excludeAllHeaders drops every header from notes (vs the default Cache-Control/Pragma-only exclusion).
Capture("exclude-all-headers", HttpWithHeaders(), arrowColors: false, excludeAllHeaders: true);
// truncateNotesAfterLines caps each note at N lines, the rest replaced by a trailing "..." line.
Capture("truncate-notes", TruncateCorpus(), arrowColors: false, truncateNotesAfterLines: 3);
// dependencyColors overrides a category's colour (arrow + participant); serviceTypeOverrides remaps a
// service's category (changing its shape + colour). caller-category proves the pure-caller shape + the
// arrow-colour fallback to the caller; pure-caller-order proves the non-service caller is declared first.
Capture("dependency-colors", SimpleHttp(), arrowColors: true, participantColors: true,
    dependencyColors: new() { ["HTTP"] = "#123456" });
Capture("service-type-overrides", SimpleHttp(), arrowColors: true, participantColors: true,
    serviceTypeOverrides: new() { ["OrderService"] = "Redis" });
Capture("caller-category", CallerCategoryCorpus(), arrowColors: true, participantColors: true);
Capture("pure-caller-order", PureCallerOrderCorpus(), arrowColors: false);

void Capture(string name, List<RequestResponseLog> logs, bool arrowColors, bool participantColors = false,
    string? plantUmlTheme = null, bool separateSetup = false, bool highlightSetup = true,
    string? setupHighlightColor = null, GraphQlBodyFormat graphQlBodyFormat = GraphQlBodyFormat.FormattedWithMetadata,
    bool internalFlowTracking = false, int truncateNotesAfterLines = 0, bool excludeAllHeaders = false,
    Dictionary<string, string>? dependencyColors = null, Dictionary<string, string>? serviceTypeOverrides = null)
{
    var results = PlantUmlCreator.GetPlantUmlImageTagsPerTestId(
        logs,
        sequenceDiagramArrowColors: arrowColors,
        sequenceDiagramParticipantColors: participantColors,
        plantUmlTheme: plantUmlTheme,
        separateSetup: separateSetup,
        highlightSetup: highlightSetup,
        setupHighlightColor: setupHighlightColor,
        truncateNotesAfterLines: truncateNotesAfterLines,
        excludeAllHeaders: excludeAllHeaders,
        dependencyColors: dependencyColors,
        serviceTypeOverrides: serviceTypeOverrides,
        graphQlBodyFormat: graphQlBodyFormat,
        internalFlowTracking: internalFlowTracking,
        clientSideSplitting: true);

    foreach (var test in results)
    {
        var diagram = test.PlantUmls.First().Item1;
        var file = Path.Combine(outDir, $"{name}.puml");
        File.WriteAllText(file, diagram.ReplaceLineEndings("\n"));
        Console.WriteLine($"=== {name} ({diagram.Length} chars) ===");
        Console.WriteLine(diagram);
        Console.WriteLine();
    }
}

// --- corpora ---

static (string, string?)[] NoHeaders() => Array.Empty<(string, string?)>();

static List<RequestResponseLog> NoteOnRightCorpus()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"item\":\"egg\"}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP") { NoteOnRight = true },
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> BinaryContent()
{
    var (trace, rr) = Ids(1);
    var binary = "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u000e\u000fbin"; // 10/13 control → binary
    return
    [
        new RequestResponseLog("Uploads a file", "t1", HttpMethod.Post, binary,
            new Uri("http://files/upload"), NoHeaders(), "FileService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Uploads a file", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://files/upload"), NoHeaders(), "FileService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> FormEncoded()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Submits a form", "t1", HttpMethod.Post, "item=egg&qty=2&note=hello world",
            new Uri("http://cart/add"), NoHeaders(), "CartService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Submits a form", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://cart/add"), NoHeaders(), "CartService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> LongUrl()
{
    var (trace, rr) = Ids(1);
    var longPath = "/api/v1/orders/search?filter=status:pending,priority:high&sort=created_desc"
        + "&page=1&size=50&include=items,customer,shipping,billing,payments,history";
    return
    [
        new RequestResponseLog("Searches orders", "t1", HttpMethod.Get, null,
            new Uri("http://orders" + longPath), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> FocusCorpus()
{
    var (trace, rr) = Ids(1);
    var content = "{\"orderId\":\"A-100\",\"customer\":{\"name\":\"Acme\",\"tier\":\"gold\"},\"total\":50}";
    return
    [
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post, content,
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP")
            { FocusFields = ["orderId"] },
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> SetupCorpus()
{
    var (t1, r1) = Ids(1);
    var (t2, r2) = Ids(2);
    var (t3, r3) = Ids(3);
    // A setup-phase GET, an IsActionStart marker (skipped), then the action-phase POST.
    return
    [
        new RequestResponseLog("Places an order", "t1", HttpMethod.Get, null, new Uri("http://config/settings"), NoHeaders(), "ConfigService", "Test", RequestResponseType.Request, t1, r1, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Places an order", "t1", HttpMethod.Get, null, new Uri("http://config/settings"), NoHeaders(), "ConfigService", "Test", RequestResponseType.Response, t1, r1, false, StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
        new RequestResponseLog("Places an order", "t1", HttpMethod.Get, null, new Uri("http://marker/"), NoHeaders(), "Marker", "Test", RequestResponseType.Request, t2, r2, false) { IsActionStart = true },
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post, "{\"item\":\"egg\"}", new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test", RequestResponseType.Request, t3, r3, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post, "{\"ok\":true}", new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test", RequestResponseType.Response, t3, r3, false, StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> HttpWithHeaders()
{
    var (trace, rr) = Ids(1);
    // Headers exercise the gray [Key=Value] rendering, ordinal sort, and the default Cache-Control exclusion.
    (string, string?)[] reqHeaders = [("Content-Type", "application/json"), ("Accept", "application/json"), ("Cache-Control", "no-cache")];
    (string, string?)[] respHeaders = [("Content-Type", "application/json"), ("X-Trace", "abc-123")];
    return
    [
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"item\":\"egg\"}",
            new Uri("http://orders/checkout"), reqHeaders, "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"), respHeaders, "OrderService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> GraphQlQuery()
{
    var (trace, rr) = Ids(1);
    // Named operation (query GetUser) → "query GetUser" arrow label; variables carry <>&+ to exercise the
    // default/HTML-safe encoder (escaped to < > & +). Headers appear above in the
    // default FormattedWithMetadata mode and are suppressed in FormattedQueryOnly.
    var body = "{\"query\":\"query GetUser($id: ID!) { user(id: $id) { id name email } }\"," +
        "\"variables\":{\"id\":\"42\",\"note\":\"a<b>&c+d\"}}";
    (string, string?)[] reqHeaders = [("Content-Type", "application/json"), ("Authorization", "Bearer xyz")];
    return
    [
        new RequestResponseLog("GraphQL query", "t1", HttpMethod.Post, body,
            new Uri("http://api/graphql"), reqHeaders, "ApiService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("GraphQL query", "t1", HttpMethod.Post,
            "{\"data\":{\"user\":{\"id\":\"42\",\"name\":\"Ada\"}}}",
            new Uri("http://api/graphql"), NoHeaders(), "ApiService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> GraphQlMutation()
{
    var (trace, rr) = Ids(1);
    // Anonymous operation + explicit operationName → "mutation PlaceOrder" (operationName override branch).
    // The createOrder(input: {sku: $sku}) parentheses keep their inner braces inline.
    var body = "{\"query\":\"mutation { createOrder(input: {sku: $sku}) { id status } }\"," +
        "\"operationName\":\"PlaceOrder\",\"variables\":{\"sku\":\"ABC\"}}";
    return
    [
        new RequestResponseLog("GraphQL mutation", "t1", HttpMethod.Post, body,
            new Uri("http://api/graphql"), NoHeaders(), "ApiService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("GraphQL mutation", "t1", HttpMethod.Post,
            "{\"data\":{\"createOrder\":{\"id\":\"o-1\",\"status\":\"PLACED\"}}}",
            new Uri("http://api/graphql"), NoHeaders(), "ApiService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> CallerCategoryCorpus()
{
    var (trace, rr) = Ids(1);
    // The caller "EventBus" carries a CallerDependencyCategory (ServiceBus) and is the pure caller →
    // shaped as a queue (not an actor). The service "Handler" has no category, so its arrow colour falls
    // back to the caller's category (ServiceBus → MessageQueue → #9B59B6).
    return
    [
        new RequestResponseLog("Handles an event", "t1", HttpMethod.Post, "{\"id\":1}",
            new Uri("http://handler/process"), NoHeaders(), "Handler", "EventBus",
            RequestResponseType.Request, trace, rr, false, CallerDependencyCategory: "ServiceBus"),
        new RequestResponseLog("Handles an event", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://handler/process"), NoHeaders(), "Handler", "EventBus",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, CallerDependencyCategory: "ServiceBus"),
    ];
}

static List<RequestResponseLog> PureCallerOrderCorpus()
{
    var (t1, r1) = Ids(1);
    var (t2, r2) = Ids(2);
    // First trace ServiceA → ServiceB (ServiceA is a caller here but a service in the second trace);
    // second trace Test → ServiceA. Test never appears as a service → it is the pure caller, declared
    // FIRST (the .NET ordering the simpler first-seen algorithm got wrong: it emitted ServiceA, ServiceB,
    // Test).
    return
    [
        new RequestResponseLog("Chained call", "t1", HttpMethod.Post, "{\"x\":1}",
            new Uri("http://b/op"), NoHeaders(), "ServiceB", "ServiceA",
            RequestResponseType.Request, t1, r1, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Chained call", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://b/op"), NoHeaders(), "ServiceB", "ServiceA",
            RequestResponseType.Response, t1, r1, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
        new RequestResponseLog("Chained call", "t1", HttpMethod.Post, "{\"y\":2}",
            new Uri("http://a/op"), NoHeaders(), "ServiceA", "Test",
            RequestResponseType.Request, t2, r2, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Chained call", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://a/op"), NoHeaders(), "ServiceA", "Test",
            RequestResponseType.Response, t2, r2, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> TruncateCorpus()
{
    var (trace, rr) = Ids(1);
    // A multi-field JSON body that pretty-prints to >3 lines, so truncateNotesAfterLines:3 visibly cuts it.
    return
    [
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post,
            "{\"orderId\":\"A-100\",\"item\":\"egg\",\"qty\":2}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post,
            "{\"ok\":true,\"id\":\"A-100\",\"status\":\"placed\"}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> GraphQlComplex()
{
    var (trace, rr) = Ids(1);
    // Exercises the query formatter's harder branches: a named operation, an @include directive (stays
    // attached), an alias (short:), a fragment spread (...userFields), an inline fragment (... on Admin),
    // and a top-level fragment definition (double newline between top-level constructs).
    var query = "query GetData($flag: Boolean!) { user { id name @include(if: $flag) short: emailAddress "
        + "...userFields ... on Admin { role } } } fragment userFields on User { createdAt status }";
    var body = "{\"query\":\"" + query + "\",\"variables\":{\"flag\":true}}";
    return
    [
        new RequestResponseLog("GraphQL complex", "t1", HttpMethod.Post, body,
            new Uri("http://api/graphql"), NoHeaders(), "ApiService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("GraphQL complex", "t1", HttpMethod.Post, "{\"data\":{\"user\":{\"id\":\"1\"}}}",
            new Uri("http://api/graphql"), NoHeaders(), "ApiService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

void CaptureGraphQlLabels()
{
    // Pure GraphQlOperationDetector branch coverage: each input → its real .NET label (or "null"),
    // TAB-separated. The Java GraphQlOperationDetectorParityTest reads the same fixture and asserts parity.
    string[] inputs =
    [
        "{\"query\":\"query GetUser { user { id } }\"}",            // named query
        "{\"query\":\"mutation CreateOrder { x }\"}",               // named mutation
        "{\"query\":\"subscription OnMsg { x }\"}",                 // named subscription
        "{\"query\":\"{ user { name } }\"}",                        // anonymous shorthand → "query"
        "{\"query\":\"query { x }\",\"operationName\":\"Named\"}",  // operationName supplies the name
        "{\"query\":\"SELECT * FROM users\"}",                      // not GraphQL → null
        "{\"foo\":\"bar\"}",                                        // no query key → null
        "SELECT 1",                                                 // not a JSON object → null
        "{\"data\":{\"query\":\"query Nested { x }\"}}",            // query key nested, not top-level → null
        "{\"query\":\"   query   Spaced   { x }\"}",                // literal leading whitespace
        "{\"query\":\"\\n\\tquery GetX { x }\"}",                   // JSON-escaped leading whitespace
        "{\"query\":\"query GetUser { x }\",\"operationName\":\"Override\"}", // operationName overrides inline
    ];
    var lines = inputs.Select(input => $"{input}\t{GraphQlOperationDetector.TryExtractLabel(input) ?? "null"}");
    File.WriteAllText(Path.Combine(outDir, "graphql-labels.txt"), string.Join("\n", lines) + "\n");
}

void CaptureHtmlEscapeSamples()
{
    // Pins System.Net.WebUtility.HtmlEncode (what the report uses) against the Java HtmlEscaper for the
    // cases the report goldens never exercise: the apostrophe, the C1-control range (0x80–0x9F), Latin-1
    // supplement (≥0xA0), a BMP symbol, an astral emoji (surrogate pair), and raw whitespace controls.
    // Each line is "<input>\t<expected>" with every non-printable-ASCII char written as \uXXXX so the TSV
    // stays clean ASCII; the Java HtmlEscaperParityTest un-escapes both columns and compares.
    string[] inputs =
    [
        "plain text",
        "a<b>c",
        "a&b",
        "a\"b",
        "a'b",
        "caf\u00E9",
        "\u2713\u20AC",
        "\u0080\u0085\u009F",
        "\u00A0\u00FF",
        "\uD83D\uDE00",
        "tab\tnl\ncr\r",
        "<a href=\"x\">&'\u00E9\uD83D\uDE00",
    ];
    var lines = inputs.Select(s => FixtureEscape(s) + "\t" + FixtureEscape(System.Net.WebUtility.HtmlEncode(s)));
    File.WriteAllText(Path.Combine(outDir, "html-escape-samples.txt"), string.Join("\n", lines) + "\n");

    static string FixtureEscape(string s)
    {
        var sb = new System.Text.StringBuilder(s.Length);
        foreach (var c in s)
        {
            if (c >= 0x20 && c <= 0x7E && c != '\\')
                sb.Append(c);
            else
                sb.Append("\\u").Append(((int)c).ToString("X4"));
        }
        return sb.ToString();
    }
}

static List<RequestResponseLog> SimpleHttp()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"item\":\"egg\"}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> MultiTrace()
{
    var (t1, r1) = Ids(1);
    var (t2, r2) = Ids(2);
    return
    [
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"item\":\"egg\"}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Request, t1, r1, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Checkout succeeds", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Response, t1, r1, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
        new RequestResponseLog("Checkout succeeds", "t1", (OneOf<HttpMethod, string>)"SELECT",
            "SELECT * FROM orders WHERE id = 1",
            new Uri("sql://database/"), NoHeaders(), "OrderDb", "Test",
            RequestResponseType.Request, t2, r2, false, DependencyCategory: "SQL"),
        new RequestResponseLog("Checkout succeeds", "t1", (OneOf<HttpMethod, string>)"SELECT",
            "1 row",
            new Uri("sql://database/"), NoHeaders(), "OrderDb", "Test",
            RequestResponseType.Response, t2, r2, false,
            StatusCode: (OneOf<HttpStatusCode, string>)"OK", DependencyCategory: "SQL"),
    ];
}

static List<RequestResponseLog> Sql()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Lookup order", "t1", (OneOf<HttpMethod, string>)"SELECT",
            "SELECT * FROM orders WHERE id = 1",
            new Uri("sql://database/"), NoHeaders(), "OrderDb", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "SQL"),
        new RequestResponseLog("Lookup order", "t1", (OneOf<HttpMethod, string>)"SELECT",
            "1 row",
            new Uri("sql://database/"), NoHeaders(), "OrderDb", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: (OneOf<HttpStatusCode, string>)"OK", DependencyCategory: "SQL"),
    ];
}

static List<RequestResponseLog> Event()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Publishes order-created", "t1", (OneOf<HttpMethod, string>)"PUBLISH",
            "{\"id\":1}",
            new Uri("amqp://bus/"), NoHeaders(), "Kafka", "Test",
            RequestResponseType.Request, trace, rr, false,
            MetaType: RequestResponseMetaType.Event, DependencyCategory: "MessageQueue"),
        new RequestResponseLog("Publishes order-created", "t1", (OneOf<HttpMethod, string>)"PUBLISH",
            null,
            new Uri("amqp://bus/"), NoHeaders(), "Kafka", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: (OneOf<HttpStatusCode, string>)"Sent",
            MetaType: RequestResponseMetaType.Event, DependencyCategory: "MessageQueue"),
    ];
}

static List<RequestResponseLog> Redis()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Caches the cart", "t1", (OneOf<HttpMethod, string>)"GET",
            "cart:42",
            new Uri("redis://cache/"), NoHeaders(), "CartCache", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "Redis"),
        new RequestResponseLog("Caches the cart", "t1", (OneOf<HttpMethod, string>)"GET",
            "{\"items\":2}",
            new Uri("redis://cache/"), NoHeaders(), "CartCache", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: (OneOf<HttpStatusCode, string>)"OK", DependencyCategory: "Redis"),
    ];
}

static List<RequestResponseLog> Storage()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Uploads the receipt", "t1", (OneOf<HttpMethod, string>)"PUT",
            "receipts/42.pdf",
            new Uri("s3://storage/"), NoHeaders(), "ReceiptBucket", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "S3"),
        new RequestResponseLog("Uploads the receipt", "t1", (OneOf<HttpMethod, string>)"PUT",
            null,
            new Uri("s3://storage/"), NoHeaders(), "ReceiptBucket", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "S3"),
    ];
}

static List<RequestResponseLog> Unknown()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Calls a widget", "t1", (OneOf<HttpMethod, string>)"INVOKE",
            "ping",
            new Uri("widget://thing/"), NoHeaders(), "WidgetService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "Widget"),
        new RequestResponseLog("Calls a widget", "t1", (OneOf<HttpMethod, string>)"INVOKE",
            "pong",
            new Uri("widget://thing/"), NoHeaders(), "WidgetService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: (OneOf<HttpStatusCode, string>)"OK", DependencyCategory: "Widget"),
    ];
}

static List<RequestResponseLog> StatusCodes()
{
    var (t1, r1) = Ids(1);
    var (t2, r2) = Ids(2);
    var (t3, r3) = Ids(3);
    return
    [
        new RequestResponseLog("Status variations", "t1", HttpMethod.Get, null,
            new Uri("http://api/missing"), NoHeaders(), "Api", "Test",
            RequestResponseType.Request, t1, r1, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Status variations", "t1", HttpMethod.Get, null,
            new Uri("http://api/missing"), NoHeaders(), "Api", "Test",
            RequestResponseType.Response, t1, r1, false,
            StatusCode: HttpStatusCode.NotFound, DependencyCategory: "HTTP"),
        new RequestResponseLog("Status variations", "t1", HttpMethod.Post, null,
            new Uri("http://api/boom"), NoHeaders(), "Api", "Test",
            RequestResponseType.Request, t2, r2, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Status variations", "t1", HttpMethod.Post, null,
            new Uri("http://api/boom"), NoHeaders(), "Api", "Test",
            RequestResponseType.Response, t2, r2, false,
            StatusCode: HttpStatusCode.InternalServerError, DependencyCategory: "HTTP"),
        new RequestResponseLog("Status variations", "t1", HttpMethod.Get, null,
            new Uri("http://api/old"), NoHeaders(), "Api", "Test",
            RequestResponseType.Request, t3, r3, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Status variations", "t1", HttpMethod.Get, null,
            new Uri("http://api/old"), NoHeaders(), "Api", "Test",
            RequestResponseType.Response, t3, r3, false,
            StatusCode: HttpStatusCode.Redirect, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> Escaping()
{
    var (trace, rr) = Ids(1);
    return
    [
        new RequestResponseLog("Handles tricky content", "t1", HttpMethod.Post,
            "{\"path\":\"C:\\\\temp\\\\f\",\"name\":\"<a>&\\\"x\\\"\",\"emoji\":\"✓\",\"nested\":{\"a\":[1,2]}}",
            new Uri("http://api/echo"), NoHeaders(), "EchoService", "Test",
            RequestResponseType.Request, trace, rr, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Handles tricky content", "t1", HttpMethod.Post,
            "plain text with a backslash \\ and a quote \"",
            new Uri("http://api/echo"), NoHeaders(), "EchoService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
    ];
}

static List<RequestResponseLog> FanOut()
{
    var (t1, r1) = Ids(1);
    var (t2, r2) = Ids(2);
    var (t3, r3) = Ids(3);
    return
    [
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post, "{\"item\":\"egg\"}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Request, t1, r1, false, DependencyCategory: "HTTP"),
        new RequestResponseLog("Places an order", "t1", HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"), NoHeaders(), "OrderService", "Test",
            RequestResponseType.Response, t1, r1, false,
            StatusCode: HttpStatusCode.OK, DependencyCategory: "HTTP"),
        new RequestResponseLog("Places an order", "t1", (OneOf<HttpMethod, string>)"SELECT",
            "SELECT * FROM orders WHERE id = 1",
            new Uri("sql://database/"), NoHeaders(), "OrderDb", "Test",
            RequestResponseType.Request, t2, r2, false, DependencyCategory: "SQL"),
        new RequestResponseLog("Places an order", "t1", (OneOf<HttpMethod, string>)"SELECT",
            "1 row",
            new Uri("sql://database/"), NoHeaders(), "OrderDb", "Test",
            RequestResponseType.Response, t2, r2, false,
            StatusCode: (OneOf<HttpStatusCode, string>)"OK", DependencyCategory: "SQL"),
        new RequestResponseLog("Places an order", "t1", (OneOf<HttpMethod, string>)"GET",
            "cart:42",
            new Uri("redis://cache/"), NoHeaders(), "CartCache", "Test",
            RequestResponseType.Request, t3, r3, false, DependencyCategory: "Redis"),
        new RequestResponseLog("Places an order", "t1", (OneOf<HttpMethod, string>)"GET",
            "{\"items\":2}",
            new Uri("redis://cache/"), NoHeaders(), "CartCache", "Test",
            RequestResponseType.Response, t3, r3, false,
            StatusCode: (OneOf<HttpStatusCode, string>)"OK", DependencyCategory: "Redis"),
    ];
}

static (Guid, Guid) Ids(int n) =>
    (Guid.Parse($"00000000-0000-0000-0000-0000000000{n:x2}"),
     Guid.Parse($"00000000-0000-0000-0000-0000000001{n:x2}"));
