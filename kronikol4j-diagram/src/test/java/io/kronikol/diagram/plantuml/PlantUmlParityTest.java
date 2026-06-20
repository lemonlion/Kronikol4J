package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
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
 * <strong>byte-identical</strong> to the real .NET Kronikol for the same corpus. The {@code .puml}
 * fixtures were captured by driving the actual .NET {@code PlantUmlCreator}
 * ({@code parity-harness/dotnet-capture}). Only the trailing newline is normalised — never styling.
 *
 * <p>The corpus is currently the subset the Java generator covers; it grows as fidelity grows. Within
 * it, this is genuine byte parity, not a structural approximation.
 */
class PlantUmlParityTest {

    private static final UUID TRACE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID RR = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void simpleHttpMatchesDotNetByteForByte() throws IOException {
        List<RequestResponseLog> corpus = List.of(
            RequestResponseLog.builder()
                .testName("Checkout succeeds").testId("t1")
                .method(Method.Http.POST).uri(URI.create("http://orders/checkout"))
                .serviceName("OrderService").callerName("Test")
                .type(RequestResponseType.REQUEST).traceId(TRACE).requestResponseId(RR)
                .dependencyCategory(DependencyCategories.HTTP).content("{\"item\":\"egg\"}")
                .build(),
            RequestResponseLog.builder()
                .testName("Checkout succeeds").testId("t1")
                .method(Method.Http.POST).uri(URI.create("http://orders/checkout"))
                .serviceName("OrderService").callerName("Test")
                .type(RequestResponseType.RESPONSE).traceId(TRACE).requestResponseId(RR)
                .dependencyCategory(DependencyCategories.HTTP).statusCode(StatusCode.of(200))
                .content("{\"ok\":true}")
                .build());

        String actual = PlantUmlCreator.create(corpus).get(0).diagrams().get(0);

        assertThat(actual.stripTrailing()).isEqualTo(readFixture("parity/simple-http.puml").stripTrailing());
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
