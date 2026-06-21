package io.kronikol.diagram.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the component-diagram aggregation + shape logic. Byte-for-byte cross-runtime
 * parity is in {@code PlantUmlParityTest.componentDiagram}; this pins the per-relationship maths and
 * the per-dependency-type shapes that a single fixture doesn't exercise.
 */
class ComponentDiagramGeneratorTest {

    @Test
    void aggregatesCallsMethodsAndDistinctTestsPerRelationship() {
        var logs = List.of(
            req("t1", "OrderService", DependencyCategories.HTTP, Method.Http.GET, "http://o/a"),
            req("t2", "OrderService", DependencyCategories.HTTP, Method.Http.POST, "http://o/b"),
            req("t2", "OrderService", DependencyCategories.HTTP, Method.Http.GET, "http://o/c"));

        var relationships = ComponentDiagramGenerator.extractRelationships(logs);

        assertThat(relationships).hasSize(1); // same caller/service/protocol → one relationship
        var rel = relationships.get(0);
        assertThat(rel.callCount()).isEqualTo(3);
        assertThat(rel.testCount()).isEqualTo(2);                 // t1, t2 distinct
        assertThat(rel.methods()).containsExactlyInAnyOrder("GET", "POST");
        assertThat(rel.protocol()).isEqualTo("HTTP");
    }

    @Test
    void onlyRequestsAndNonIgnoredLogsCount() {
        var request = req("t1", "OrderService", DependencyCategories.HTTP, Method.Http.GET, "http://o/a");
        var response = request.toBuilder().type(RequestResponseType.RESPONSE).build();
        var ignored = req("t1", "Other", DependencyCategories.HTTP, Method.Http.GET, "http://x/")
            .toBuilder().trackingIgnore(true).build();

        var relationships = ComponentDiagramGenerator.extractRelationships(List.of(request, response, ignored));

        assertThat(relationships).hasSize(1);
        assertThat(relationships.get(0).service()).isEqualTo("OrderService");
        assertThat(relationships.get(0).callCount()).isEqualTo(1); // response shares the request's relationship
    }

    @Test
    void rendersPerDependencyTypeShapesAndThePersonCaller() {
        var logs = List.of(
            req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/"),
            req("t1", "Cache", DependencyCategories.REDIS, Method.of("GET"), "redis://c/"),
            req("t1", "Bus", DependencyCategories.MESSAGE_QUEUE, Method.of("SEND"), "mq://b/"),
            req("t1", "Widget", "Widget", Method.of("PING"), "widget://w/"),      // unknown → rectangle
            req("t1", "Api", DependencyCategories.HTTP, Method.Http.GET, "http://a/")); // http → rectangle

        var uml = ComponentDiagramGenerator.generatePlantUml(
            ComponentDiagramGenerator.extractRelationships(logs));

        assertThat(uml)
            .contains("rectangle \"**Test**\\n<size:10>[Person]</size>\" as test <<person>>")
            .contains("database \"OrderDb\" as orderDb")
            .contains("collections \"Cache\" as cache")
            .contains("queue \"Bus\" as bus")
            .contains("rectangle \"**Widget**\\n<size:10>[Software System]</size>\" as widget <<system>>")
            .contains("rectangle \"**Api**\\n<size:10>[Software System]</size>\" as api <<system>>")
            .contains("test -[#E74C3C]-> orderDb : \"SQL: SELECT - 1 calls across 1 tests\"")
            .startsWith("@startuml\nleft to right direction")
            .endsWith("@enduml");
    }

    private static RequestResponseLog req(String testId, String service, String category,
                                          Method method, String uri) {
        return RequestResponseLog.builder()
            .testName(testId).testId(testId).method(method).uri(URI.create(uri))
            .serviceName(service).callerName("Test").type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .dependencyCategory(category)
            .build();
    }
}
