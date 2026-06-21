package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cross-runtime golden-file parity: asserts the Java {@link PlantUmlCreator} produces PlantUML
 * <strong>byte-identical</strong> to the real .NET Kronikol for the same corpora. The {@code .puml}
 * fixtures were captured by driving the actual .NET {@code PlantUmlCreator}
 * ({@code parity-harness/dotnet-capture}). Only the trailing newline is normalised — never styling.
 */
class PlantUmlParityTest {

    @Test
    void simpleHttp() throws IOException {
        // .puml captured with arrowColors:false — pass false explicitly (the create default is now on).
        assertParity("simple-http", PlantUmlCreator.create(httpExchange(), false).get(0).diagrams().get(0));
    }

    @Test
    void simpleHttpColored() throws IOException {
        // .NET default is coloured arrows; verify per-dependency-type colours byte-for-byte.
        assertParity("simple-http-colored",
            PlantUmlCreator.create(httpExchange(), true).get(0).diagrams().get(0));
    }

    @Test
    void multiTrace() throws IOException {
        List<RequestResponseLog> corpus = new java.util.ArrayList<>(httpExchange());
        corpus.addAll(sqlExchange("Checkout succeeds"));
        assertParity("multi-trace", PlantUmlCreator.create(corpus, false).get(0).diagrams().get(0));
    }

    @Test
    void sql() throws IOException {
        assertParity("sql", PlantUmlCreator.create(sqlExchange("Lookup order"), false).get(0).diagrams().get(0));
    }

    @Test
    void event() throws IOException {
        assertParity("event", PlantUmlCreator.create(eventExchange(), false).get(0).diagrams().get(0));
    }

    @Test
    void redis() throws IOException {
        // cache → collections shape, #F39C12 arrow colour.
        assertParity("redis", PlantUmlCreator.create(redisExchange(), true).get(0).diagrams().get(0));
    }

    @Test
    void storage() throws IOException {
        // S3 → storage type → database shape, #2ECC71 arrow colour.
        assertParity("storage", PlantUmlCreator.create(storageExchange(), true).get(0).diagrams().get(0));
    }

    @Test
    void unknownCategory() throws IOException {
        // unknown category → participant shape, #95A5A6 arrow colour.
        assertParity("unknown-category",
            PlantUmlCreator.create(unknownExchange(), true).get(0).diagrams().get(0));
    }

    @Test
    void statusCodes() throws IOException {
        // 404 → "Not Found", 500 → "Internal Server Error", 302 → "Found (Redirect)".
        assertParity("status-codes", PlantUmlCreator.create(statusCodesCorpus(), false).get(0).diagrams().get(0));
    }

    @Test
    void escaping() throws IOException {
        // Backslash-doubling, no HTML-escaping in notes, unicode, and nested-JSON pretty-printing.
        assertParity("escaping", PlantUmlCreator.create(escapingExchange(), false).get(0).diagrams().get(0));
    }

    @Test
    void fanOut() throws IOException {
        // One test calling three services: first-seen participant order + per-type arrow colours.
        assertParity("fan-out", PlantUmlCreator.create(fanOutCorpus(), true).get(0).diagrams().get(0));
    }

    @Test
    void participantColors() throws IOException {
        // sequenceDiagramParticipantColors: the dependency colour is appended to each categorised
        // participant declaration; the un-categorised actor "Test" stays uncoloured.
        assertParity("participant-colors",
            PlantUmlCreator.create(httpExchange(), false, true).get(0).diagrams().get(0));
    }

    @Test
    void participantColorsFanOut() throws IOException {
        // Combined arrow + participant colours across three shapes (entity/database/collections).
        assertParity("participant-colors-fanout",
            PlantUmlCreator.create(fanOutCorpus(), true, true).get(0).diagrams().get(0));
    }

    // --- corpora (built identically to parity-harness/dotnet-capture/Program.cs) ---

    private static List<RequestResponseLog> httpExchange() {
        return List.of(
            log("Checkout succeeds", Method.Http.POST, "http://orders/checkout", "OrderService",
                DependencyCategories.HTTP, RequestResponseType.REQUEST, "{\"item\":\"egg\"}", null),
            log("Checkout succeeds", Method.Http.POST, "http://orders/checkout", "OrderService",
                DependencyCategories.HTTP, RequestResponseType.RESPONSE, "{\"ok\":true}", StatusCode.of(200)));
    }

    private static List<RequestResponseLog> sqlExchange(String testName) {
        return List.of(
            log(testName, Method.of("SELECT"), "sql://database/", "OrderDb",
                DependencyCategories.SQL, RequestResponseType.REQUEST,
                "SELECT * FROM orders WHERE id = 1", null),
            log(testName, Method.of("SELECT"), "sql://database/", "OrderDb",
                DependencyCategories.SQL, RequestResponseType.RESPONSE, "1 row", StatusCode.of("OK")));
    }

    private static List<RequestResponseLog> eventExchange() {
        return List.of(
            event(RequestResponseType.REQUEST, "{\"id\":1}", null),
            event(RequestResponseType.RESPONSE, null, StatusCode.of("Sent")));
    }

    private static List<RequestResponseLog> redisExchange() {
        return List.of(
            log("Caches the cart", Method.of("GET"), "redis://cache/", "CartCache",
                DependencyCategories.REDIS, RequestResponseType.REQUEST, "cart:42", null),
            log("Caches the cart", Method.of("GET"), "redis://cache/", "CartCache",
                DependencyCategories.REDIS, RequestResponseType.RESPONSE, "{\"items\":2}", StatusCode.of("OK")));
    }

    private static List<RequestResponseLog> storageExchange() {
        return List.of(
            log("Uploads the receipt", Method.of("PUT"), "s3://storage/", "ReceiptBucket",
                DependencyCategories.S3, RequestResponseType.REQUEST, "receipts/42.pdf", null),
            log("Uploads the receipt", Method.of("PUT"), "s3://storage/", "ReceiptBucket",
                DependencyCategories.S3, RequestResponseType.RESPONSE, null, StatusCode.of(200)));
    }

    private static List<RequestResponseLog> unknownExchange() {
        return List.of(
            log("Calls a widget", Method.of("INVOKE"), "widget://thing/", "WidgetService",
                "Widget", RequestResponseType.REQUEST, "ping", null),
            log("Calls a widget", Method.of("INVOKE"), "widget://thing/", "WidgetService",
                "Widget", RequestResponseType.RESPONSE, "pong", StatusCode.of("OK")));
    }

    private static List<RequestResponseLog> statusCodesCorpus() {
        return List.of(
            log("Status variations", Method.Http.GET, "http://api/missing", "Api",
                DependencyCategories.HTTP, RequestResponseType.REQUEST, null, null),
            log("Status variations", Method.Http.GET, "http://api/missing", "Api",
                DependencyCategories.HTTP, RequestResponseType.RESPONSE, null, StatusCode.of(404)),
            log("Status variations", Method.Http.POST, "http://api/boom", "Api",
                DependencyCategories.HTTP, RequestResponseType.REQUEST, null, null),
            log("Status variations", Method.Http.POST, "http://api/boom", "Api",
                DependencyCategories.HTTP, RequestResponseType.RESPONSE, null, StatusCode.of(500)),
            log("Status variations", Method.Http.GET, "http://api/old", "Api",
                DependencyCategories.HTTP, RequestResponseType.REQUEST, null, null),
            log("Status variations", Method.Http.GET, "http://api/old", "Api",
                DependencyCategories.HTTP, RequestResponseType.RESPONSE, null, StatusCode.of(302)));
    }

    private static List<RequestResponseLog> escapingExchange() {
        return List.of(
            log("Handles tricky content", Method.Http.POST, "http://api/echo", "EchoService",
                DependencyCategories.HTTP, RequestResponseType.REQUEST,
                "{\"path\":\"C:\\\\temp\\\\f\",\"name\":\"<a>&\\\"x\\\"\",\"emoji\":\"✓\",\"nested\":{\"a\":[1,2]}}",
                null),
            log("Handles tricky content", Method.Http.POST, "http://api/echo", "EchoService",
                DependencyCategories.HTTP, RequestResponseType.RESPONSE,
                "plain text with a backslash \\ and a quote \"", StatusCode.of(200)));
    }

    private static List<RequestResponseLog> fanOutCorpus() {
        List<RequestResponseLog> corpus = new java.util.ArrayList<>();
        corpus.add(log("Places an order", Method.Http.POST, "http://orders/checkout", "OrderService",
            DependencyCategories.HTTP, RequestResponseType.REQUEST, "{\"item\":\"egg\"}", null));
        corpus.add(log("Places an order", Method.Http.POST, "http://orders/checkout", "OrderService",
            DependencyCategories.HTTP, RequestResponseType.RESPONSE, "{\"ok\":true}", StatusCode.of(200)));
        corpus.add(log("Places an order", Method.of("SELECT"), "sql://database/", "OrderDb",
            DependencyCategories.SQL, RequestResponseType.REQUEST, "SELECT * FROM orders WHERE id = 1", null));
        corpus.add(log("Places an order", Method.of("SELECT"), "sql://database/", "OrderDb",
            DependencyCategories.SQL, RequestResponseType.RESPONSE, "1 row", StatusCode.of("OK")));
        corpus.add(log("Places an order", Method.of("GET"), "redis://cache/", "CartCache",
            DependencyCategories.REDIS, RequestResponseType.REQUEST, "cart:42", null));
        corpus.add(log("Places an order", Method.of("GET"), "redis://cache/", "CartCache",
            DependencyCategories.REDIS, RequestResponseType.RESPONSE, "{\"items\":2}", StatusCode.of("OK")));
        return corpus;
    }

    private static RequestResponseLog log(String testName, Method method, String uri, String service,
                                          String category, RequestResponseType type, String content,
                                          StatusCode status) {
        return RequestResponseLog.builder()
            .testName(testName).testId("t1").method(method).uri(URI.create(uri))
            .serviceName(service).callerName("Test").type(type)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .dependencyCategory(category).statusCode(status).content(content)
            .build();
    }

    private static RequestResponseLog event(RequestResponseType type, String content, StatusCode status) {
        return RequestResponseLog.builder()
            .testName("Publishes order-created").testId("t1").method(Method.of("PUBLISH"))
            .uri(URI.create("amqp://bus/")).serviceName("Kafka").callerName("Test").type(type)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .dependencyCategory(DependencyCategories.MESSAGE_QUEUE)
            .metaType(RequestResponseMetaType.EVENT).statusCode(status).content(content)
            .build();
    }

    private static void assertParity(String fixture, String actual) throws IOException {
        assertThat(normalize(actual))
            .isEqualTo(normalize(readFixture("parity/" + fixture + ".puml")));
    }

    /**
     * Normalises CRLF→LF and strips <em>only</em> trailing newlines — never interior content nor
     * trailing spaces/tabs. So a stray trailing space or an extra blank line still fails parity
     * (unlike {@code stripTrailing()}, which would have masked them); the sole tolerated difference
     * is the final newline at end-of-file.
     */
    private static String normalize(String text) {
        String unix = text.replace("\r\n", "\n").replace("\r", "\n");
        int end = unix.length();
        while (end > 0 && unix.charAt(end - 1) == '\n') {
            end--;
        }
        return unix.substring(0, end);
    }

    private static String readFixture(String resource) throws IOException {
        try (InputStream in = PlantUmlParityTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
