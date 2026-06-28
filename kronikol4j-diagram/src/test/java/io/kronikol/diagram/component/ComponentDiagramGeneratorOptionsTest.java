package io.kronikol.diagram.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@link ComponentDiagramRenderOptions}-driven branches of {@link ComponentDiagramGenerator}
 * (browser/non-C4 mode) — the .NET {@code ComponentDiagramOptions} render-relevant subset: custom title +
 * {@code !theme}, the relationship-label formatter, {@link ArrowColorMode}, per-category arrow-colour
 * overrides, the {@code participantFilter}, and exclusion of override/action-start markers from aggregation.
 */
class ComponentDiagramGeneratorOptionsTest {

    @Test
    void customTitleAndThemeAppearInThePreamble() {
        var rels = ComponentDiagramGenerator.extractRelationships(List.of(
            req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/")));
        var opts = ComponentDiagramRenderOptions.builder()
            .title("My Components").plantUmlTheme("cerulean").build();

        var uml = ComponentDiagramGenerator.generatePlantUml(rels, opts);

        assertThat(uml).contains("!theme cerulean\n\ntitle My Components\n");
    }

    @Test
    void defaultTitleAndNoThemeWhenUnset() {
        var rels = ComponentDiagramGenerator.extractRelationships(List.of(
            req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/")));

        var uml = ComponentDiagramGenerator.generatePlantUml(rels, ComponentDiagramRenderOptions.defaults());

        assertThat(uml).contains("title Component Diagram").doesNotContain("!theme");
    }

    @Test
    void performanceArrowColorModeWithoutStatsRendersPlainArrow() {
        var rels = ComponentDiagramGenerator.extractRelationships(List.of(
            req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/")));
        var opts = ComponentDiagramRenderOptions.builder().arrowColorMode(ArrowColorMode.PERFORMANCE).build();

        var uml = ComponentDiagramGenerator.generatePlantUml(rels, opts);

        assertThat(uml).contains("test --> orderDb : \"SQL: SELECT - 1 calls across 1 tests\"")
            .doesNotContain("-[#"); // no colour applied without stats
    }

    @Test
    void dependencyColorsOverrideTheArrowColour() {
        var rels = ComponentDiagramGenerator.extractRelationships(List.of(
            req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/")));
        var opts = ComponentDiagramRenderOptions.builder()
            .dependencyColors(Map.of(DependencyCategories.SQL, "#123456")).build();

        var uml = ComponentDiagramGenerator.generatePlantUml(rels, opts);

        assertThat(uml).contains("test -[#123456]-> orderDb : \"SQL: SELECT - 1 calls across 1 tests\"");
    }

    @Test
    void relationshipLabelFormatterOverridesTheLabel() {
        var rels = ComponentDiagramGenerator.extractRelationships(List.of(
            req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/")));
        var opts = ComponentDiagramRenderOptions.builder()
            .relationshipLabelFormatter(rel -> "custom:" + rel.service()).build();

        var uml = ComponentDiagramGenerator.generatePlantUml(rels, opts);

        assertThat(uml).contains("-> orderDb : \"custom:OrderDb\"");
    }

    @Test
    void participantFilterExcludesNonMatchingCallersAndServices() {
        var logs = List.of(
            req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/"),
            req("t1", "Secret", DependencyCategories.HTTP, Method.Http.GET, "http://s/"));

        var rels = ComponentDiagramGenerator.extractRelationships(logs, name -> !name.equals("Secret"));

        assertThat(rels).hasSize(1);
        assertThat(rels.get(0).service()).isEqualTo("OrderDb");
    }

    @Test
    void overrideAndActionStartMarkersAreExcludedFromAggregation() {
        var real = req("t1", "OrderDb", DependencyCategories.SQL, Method.of("SELECT"), "sql://db/");
        var overrideStart = real.toBuilder().serviceName("Marker").build().overrideStart(true);
        var actionStart = real.toBuilder().serviceName("Marker").build().actionStart(true);

        var rels = ComponentDiagramGenerator.extractRelationships(
            List.of(real, overrideStart, actionStart));

        assertThat(rels).hasSize(1);
        assertThat(rels.get(0).service()).isEqualTo("OrderDb");
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
