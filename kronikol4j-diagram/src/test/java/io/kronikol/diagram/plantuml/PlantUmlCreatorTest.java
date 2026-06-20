package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.diagram.model.PlantUmlForTest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlantUmlCreatorTest {

    private static final UUID TRACE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID RR = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private static RequestResponseLog request() {
        return RequestResponseLog.builder()
            .testName("Checkout succeeds").testId("t1")
            .method(Method.Http.POST).uri(URI.create("http://orders/checkout"))
            .serviceName("OrderService").callerName("Test")
            .type(RequestResponseType.REQUEST).traceId(TRACE).requestResponseId(RR)
            .dependencyCategory(DependencyCategories.HTTP)
            .content("{\"item\":\"egg\"}")
            .build();
    }

    private static RequestResponseLog response() {
        return RequestResponseLog.builder()
            .testName("Checkout succeeds").testId("t1")
            .method(Method.Http.POST).uri(URI.create("http://orders/checkout"))
            .serviceName("OrderService").callerName("Test")
            .type(RequestResponseType.RESPONSE).traceId(TRACE).requestResponseId(RR)
            .statusCode(StatusCode.of(200))
            .content("{\"ok\":true}")
            .build();
    }

    @Test
    void buildsOneSequenceDiagramPerTest() {
        List<PlantUmlForTest> diagrams = PlantUmlCreator.create(List.of(request(), response()));
        assertThat(diagrams).hasSize(1);
        assertThat(diagrams.get(0).testName()).isEqualTo("Checkout succeeds");
        assertThat(diagrams.get(0).diagrams()).hasSize(1); // client-side splitting: one per test
    }

    @Test
    void emitsExpectedSequenceStructure() {
        String uml = PlantUmlCreator.create(List.of(request(), response())).get(0).diagrams().get(0);

        assertThat(uml)
            .startsWith("@startuml\n")
            .contains("participant \"Test\" as Test")
            .contains("\"OrderService\" as OrderService")
            .contains("Test -> OrderService : POST /checkout")
            .contains("OrderService --> Test : OK")
            .contains("note right")
            .endsWith("@enduml");
    }

    @Test
    void prettyPrintsJsonNoteBodies() {
        String uml = PlantUmlCreator.create(List.of(request())).get(0).diagrams().get(0);
        assertThat(uml).contains("""
            {
              "item": "egg"
            }""");
    }

    @Test
    void emitsOnlyUnixNewlines() {
        String uml = PlantUmlCreator.create(List.of(request(), response())).get(0).diagrams().get(0);
        assertThat(uml).doesNotContain("\r");
    }

    @Test
    void unknownStatusCodeRendersAsNumber() {
        RequestResponseLog resp = response().toBuilder().statusCode(StatusCode.of(599)).build();
        String uml = PlantUmlCreator.create(List.of(request(), resp)).get(0).diagrams().get(0);
        assertThat(uml).contains("OrderService --> Test : 599");
    }
}
