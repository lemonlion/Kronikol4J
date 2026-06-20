// Golden-file capture harness: drives the REAL .NET Kronikol PlantUmlCreator over a fixed,
// deterministic corpus (fixed GUIDs) and writes the plain PlantUML text to ./fixtures/*.puml.
// The Java parity tests assert (after normalising volatile fields) byte-equality against these.

using System.Net;
using Kronikol.PlantUml;
using Kronikol.Tracking;

var outDir = Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "fixtures");
outDir = Path.GetFullPath(outDir);
Directory.CreateDirectory(outDir);

Capture("simple-http", SimpleHttp());

void Capture(string name, List<RequestResponseLog> logs)
{
    var results = PlantUmlCreator.GetPlantUmlImageTagsPerTestId(
        logs,
        sequenceDiagramArrowColors: false,
        sequenceDiagramParticipantColors: false,
        clientSideSplitting: true);

    foreach (var test in results)
    {
        var diagrams = test.PlantUmls.ToList();
        for (var i = 0; i < diagrams.Count; i++)
        {
            var file = Path.Combine(outDir, $"{name}.puml");
            File.WriteAllText(file, diagrams[i].Item1.ReplaceLineEndings("\n"));
            Console.WriteLine($"wrote {file} ({diagrams[i].Item1.Length} chars)");
            Console.WriteLine("---8<--- BEGIN");
            Console.WriteLine(diagrams[i].Item1);
            Console.WriteLine("---8<--- END");
        }
    }
}

static List<RequestResponseLog> SimpleHttp()
{
    var trace = Guid.Parse("00000000-0000-0000-0000-0000000000aa");
    var rr = Guid.Parse("00000000-0000-0000-0000-0000000000bb");
    return
    [
        new RequestResponseLog(
            "Checkout succeeds", "t1",
            HttpMethod.Post, "{\"item\":\"egg\"}",
            new Uri("http://orders/checkout"),
            Array.Empty<(string, string?)>(),
            "OrderService", "Test",
            RequestResponseType.Request, trace, rr, false,
            DependencyCategory: "HTTP"),
        new RequestResponseLog(
            "Checkout succeeds", "t1",
            HttpMethod.Post, "{\"ok\":true}",
            new Uri("http://orders/checkout"),
            Array.Empty<(string, string?)>(),
            "OrderService", "Test",
            RequestResponseType.Response, trace, rr, false,
            StatusCode: HttpStatusCode.OK,
            DependencyCategory: "HTTP"),
    ];
}
