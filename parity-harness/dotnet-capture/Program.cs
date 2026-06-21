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
