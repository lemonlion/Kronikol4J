package io.kronikol.report.merge;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.diagram.component.ComponentDiagramGenerator;
import io.kronikol.diagram.component.ComponentRelationship;
import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.flow.InternalFlowPopupInput;
import io.kronikol.report.flow.WholeTestFlowContent;
import io.kronikol.report.flow.WholeTestFlowInput;
import io.kronikol.report.flow.WholeTestFlowVisualization;
import io.kronikol.report.model.CiEnvironment;
import io.kronikol.report.model.CiMetadata;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.HtmlCustomization;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergeTest {

    private static Feature checkout(String scenarioName, String testId, String stepText) {
        return new Feature("Checkout", List.of(Scenario.builder(scenarioName, testId, ExecutionStatus.PASSED)
            .durationMs(10).isHappyPath(true)
            .steps(List.of(ScenarioStep.of("Given", stepText, ExecutionStatus.PASSED))).build()));
    }

    @Test
    void fragmentJsonRoundTrips_fullDetail() {
        ComponentRelationship rel = new ComponentRelationship("Test", "OrderService", "HTTP",
            new LinkedHashSet<>(List.of("POST")), 2, 1, "HTTP");
        CiMetadata ci = new CiMetadata(CiEnvironment.GITHUB_ACTIONS, "42", "main", "abc", "u", "r", "99");
        Map<String, WholeTestFlowContent> wtf = Map.of("t1",
            new WholeTestFlowContent("<div id=\"a\"></div>", "<div data-flame-z=\"B\"></div>", 7));
        ReportFragment fragment = new ReportFragment("Run", "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z",
            List.of(checkout("succeeds", "t1", "a seeded order")),
            Map.of("t1", "@startuml\nA->B\n@enduml"), List.of(rel), Map.of(), ci,
            WholeTestFlowVisualization.BOTH, wtf);

        // The full nested model + component relationships + CI + whole-test-flow survive the JSON round-trip.
        assertThat(FragmentJson.fromJson(FragmentJson.toJson(fragment))).isEqualTo(fragment);
    }

    @Test
    void mergeUnionsScenarios_aggregatesRelationships_reconcilesCiAndTimes() {
        CiMetadata ci = new CiMetadata(CiEnvironment.GITHUB_ACTIONS, "42", "main", "abc", "u", "r", "99");
        ReportFragment shard1 = new ReportFragment("Run", "2026-01-01T00:00:05Z", "2026-01-01T00:00:30Z",
            List.of(checkout("a", "t1", "step a")), Map.of("t1", "d1"),
            List.of(new ComponentRelationship("Test", "OrderService", "HTTP",
                new LinkedHashSet<>(List.of("GET")), 3, 1, "HTTP")),
            Map.of(), ci, WholeTestFlowVisualization.NONE, Map.of());
        ReportFragment shard2 = new ReportFragment(null, "2026-01-01T00:00:01Z", "2026-01-01T00:00:40Z",
            List.of(checkout("b", "t2", "step b"),
                new Feature("Payments", List.of(Scenario.passed("c", "t3")))),
            Map.of("t2", "d2"),
            List.of(new ComponentRelationship("Test", "OrderService", "HTTP",
                new LinkedHashSet<>(List.of("POST")), 4, 2, "HTTP")),
            Map.of(), null, WholeTestFlowVisualization.NONE, Map.of());

        ReportFragment merged = MergeableReportMerger.merge(List.of(shard1, shard2));

        assertThat(merged.startTime()).isEqualTo("2026-01-01T00:00:01Z"); // earliest
        assertThat(merged.endTime()).isEqualTo("2026-01-01T00:00:40Z");   // latest
        assertThat(merged.features()).extracting(Feature::displayName)
            .containsExactly("Checkout", "Payments"); // alphabetical (.NET parity)
        assertThat(merged.features().get(0).scenarios()).extracting(Scenario::testId)
            .containsExactly("t1", "t2"); // Checkout unioned across shards
        assertThat(merged.ciMetadata()).isEqualTo(ci); // first non-null reconciled
        // disjoint runners → the same caller/service/protocol relationship aggregates
        assertThat(merged.componentRelationships()).singleElement().satisfies(r -> {
            assertThat(r.callCount()).isEqualTo(7);  // 3 + 4
            assertThat(r.testCount()).isEqualTo(3);  // 1 + 2
            assertThat(r.methods()).containsExactlyInAnyOrder("GET", "POST");
        });
    }

    @Test
    void mergedReportEqualsDirectRender() {
        // A merged single shard must render byte-identically to the same data rendered as a single run —
        // proving the merge feeds the renderer the full surface (steps, component diagram, CI banner,
        // includeTestRunData summary), not a reduced subset.
        ComponentRelationship rel = new ComponentRelationship("Test", "OrderService", "HTTP",
            new LinkedHashSet<>(List.of("POST")), 2, 1, "HTTP");
        CiMetadata ci = new CiMetadata(CiEnvironment.GITHUB_ACTIONS, "42", "main", "abc", "u", "r", "99");
        ReportFragment shard = new ReportFragment("Run", "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z",
            List.of(checkout("succeeds", "t1", "a seeded order")),
            Map.of("t1", "@startuml\nTest -> OrderService\n@enduml"), List.of(rel), Map.of(), ci,
            WholeTestFlowVisualization.NONE, Map.of());

        String viaMerge = MergeableReportRenderer.renderHtml(shard);
        String direct = HtmlReportGenerator.renderHtml(
            shard.features(), shard.diagramByTestId(),
            ComponentDiagramGenerator.generatePlantUml(shard.componentRelationships()), "Run",
            true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z"),
            new HtmlCustomization(ci, null, null, null, false, false),
            WholeTestFlowInput.NONE, InternalFlowPopupInput.NONE);

        assertThat(viaMerge).isEqualTo(direct);
        // ...and that surface really is present: the step, the CI banner, and the run summary.
        assertThat(viaMerge)
            .contains("a seeded order")          // full step detail
            .contains("Test Execution Summary")  // includeTestRunData
            .contains("main");                   // CI metadata (branch)
        assertThat(io.kronikol.report.PumlData.all(viaMerge)).contains("Test -> OrderService"); // component diag
    }

    @Test
    void rendererEmbedsPrecomputedWholeTestFlowFromFragments() {
        WholeTestFlowContent c1 = new WholeTestFlowContent(
            "<div class=\"plantuml-browser iflow-diagram\" id=\"iflow-whole-t1\" data-plantuml-z=\"Z1\"></div>",
            "<div class=\"iflow-flame\" data-flame-z=\"F1\"></div>", 3);
        WholeTestFlowContent c2 = new WholeTestFlowContent(
            "<div class=\"plantuml-browser iflow-diagram\" id=\"iflow-whole-t2\" data-plantuml-z=\"Z2\"></div>",
            "<div class=\"iflow-flame\" data-flame-z=\"F2\"></div>", 4);
        ReportFragment shard1 = new ReportFragment("Merged", null, null,
            List.of(checkout("a", "t1", "step a")), Map.of("t1", "@startuml\nA->B\n@enduml"),
            List.of(), Map.of(), null, WholeTestFlowVisualization.BOTH, Map.of("t1", c1));
        ReportFragment shard2 = new ReportFragment("Merged", null, null,
            List.of(checkout("b", "t2", "step b")), Map.of("t2", "@startuml\nC->D\n@enduml"),
            List.of(), Map.of(), null, WholeTestFlowVisualization.BOTH, Map.of("t2", c2));

        ReportFragment merged = MergeableReportMerger.merge(List.of(
            FragmentJson.fromJson(FragmentJson.toJson(shard1)),
            FragmentJson.fromJson(FragmentJson.toJson(shard2))));
        assertThat(merged.wholeTestFlow()).containsOnlyKeys("t1", "t2");

        String html = MergeableReportRenderer.renderHtml(merged);
        assertThat(html)
            .contains("id=\"iflow-whole-t1\"").contains("data-flame-z=\"F1\"")
            .contains("id=\"iflow-whole-t2\"").contains("data-flame-z=\"F2\"")
            .contains("diagram-view-seq").contains("diagram-view-activity").contains("diagram-view-flame")
            .contains("renderFlameChart");
    }

    @Test
    void rendererProducesHtmlWithEmbeddedDiagrams() {
        ReportFragment fragment = new ReportFragment("Merged Run", null, null,
            List.of(checkout("a", "t1", "step a")), Map.of("t1", "@startuml\nAlice -> Bob\n@enduml"));

        String html = MergeableReportRenderer.renderHtml(fragment);
        assertThat(html)
            .contains("<title>Merged Run</title>")
            .contains("<h1>Merged Run</h1>")
            .contains("class=\"plantuml-browser\"")
            .contains("id=\"puml-data\"");
        assertThat(io.kronikol.report.PumlData.all(html)).contains("Alice -> Bob");
    }
}
