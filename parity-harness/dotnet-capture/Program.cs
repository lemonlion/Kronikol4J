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

void Capture(string name, List<RequestResponseLog> logs, bool arrowColors, bool participantColors = false)
{
    var results = PlantUmlCreator.GetPlantUmlImageTagsPerTestId(
        logs,
        sequenceDiagramArrowColors: arrowColors,
        sequenceDiagramParticipantColors: participantColors,
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
