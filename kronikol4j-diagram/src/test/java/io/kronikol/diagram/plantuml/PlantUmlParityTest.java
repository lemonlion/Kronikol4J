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
        assertParity("simple-http", PlantUmlCreator.create(httpExchange()).get(0).diagrams().get(0));
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
        assertParity("multi-trace", PlantUmlCreator.create(corpus).get(0).diagrams().get(0));
    }

    @Test
    void sql() throws IOException {
        assertParity("sql", PlantUmlCreator.create(sqlExchange("Lookup order")).get(0).diagrams().get(0));
    }

    @Test
    void event() throws IOException {
        assertParity("event", PlantUmlCreator.create(eventExchange()).get(0).diagrams().get(0));
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
        assertThat(actual.stripTrailing())
            .isEqualTo(readFixture("parity/" + fixture + ".puml").stripTrailing());
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
