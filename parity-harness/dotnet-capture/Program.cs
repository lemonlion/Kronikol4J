// Golden-file capture harness: drives the REAL .NET Kronikol PlantUmlCreator over fixed,
// deterministic corpora (fixed GUIDs) and writes the plain PlantUML to ./fixtures/*.puml.
// The Java parity tests assert (after normalising only the trailing newline) byte-equality.

using System.Net;
using Kronikol;
using Kronikol.ComponentDiagram;
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
