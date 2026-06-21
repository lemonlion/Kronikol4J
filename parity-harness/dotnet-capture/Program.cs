// Golden-file capture harness: drives the REAL .NET Kronikol PlantUmlCreator over fixed,
// deterministic corpora (fixed GUIDs) and writes the plain PlantUML to ./fixtures/*.puml.
// The Java parity tests assert (after normalising only the trailing newline) byte-equality.

using System.Net;
using Kronikol.PlantUml;
using Kronikol.Tracking;

var outDir = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "fixtures"));
Directory.CreateDirectory(outDir);

Capture("simple-http", SimpleHttp(), arrowColors: false);
Capture("simple-http-colored", SimpleHttp(), arrowColors: true);
Capture("multi-trace", MultiTrace(), arrowColors: false);
Capture("sql", Sql(), arrowColors: false);
Capture("event", Event(), arrowColors: false);

void Capture(string name, List<RequestResponseLog> logs, bool arrowColors)
{
    var results = PlantUmlCreator.GetPlantUmlImageTagsPerTestId(
        logs,
        sequenceDiagramArrowColors: arrowColors,
        sequenceDiagramParticipantColors: false,
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

static (Guid, Guid) Ids(int n) =>
    (Guid.Parse($"00000000-0000-0000-0000-0000000000{n:x2}"),
     Guid.Parse($"00000000-0000-0000-0000-0000000001{n:x2}"));
