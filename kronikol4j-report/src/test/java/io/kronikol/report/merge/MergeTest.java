package io.kronikol.report.merge;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.flow.WholeTestFlowContent;
import io.kronikol.report.merge.ReportFragment.FeatureFragment;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import io.kronikol.report.model.ExecutionStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergeTest {

    @Test
    void fragmentJsonRoundTrips() {
        ReportFragment fragment = new ReportFragment("Run", "2026-01-01T00:00:00Z", "2026-01-01T00:01:00Z",
            List.of(new FeatureFragment("Checkout", List.of(
                new ScenarioFragment("succeeds", "t1", ExecutionStatus.PASSED, 5, null, "@startuml\nx\n@enduml"),
                new ScenarioFragment("rejects", "t2", ExecutionStatus.FAILED, 7, "boom", null)))));

        ReportFragment back = FragmentJson.fromJson(FragmentJson.toJson(fragment));
        assertThat(back).isEqualTo(fragment);
    }

    @Test
    void mergeUnionsScenariosByTestIdAndReconcilesTimes() {
        ReportFragment shard1 = new ReportFragment("Run", "2026-01-01T00:00:05Z", "2026-01-01T00:00:30Z",
            List.of(new FeatureFragment("Checkout",
                List.of(new ScenarioFragment("a", "t1", ExecutionStatus.PASSED, 1, null, "d1")))));
        ReportFragment shard2 = new ReportFragment("Run", "2026-01-01T00:00:01Z", "2026-01-01T00:00:40Z",
            List.of(
                new FeatureFragment("Checkout",
                    List.of(new ScenarioFragment("b", "t2", ExecutionStatus.PASSED, 1, null, "d2"))),
                new FeatureFragment("Payments",
                    List.of(new ScenarioFragment("c", "t3", ExecutionStatus.PASSED, 1, null, null)))));

        ReportFragment merged = MergeableReportMerger.merge(List.of(shard1, shard2));

        assertThat(merged.startTime()).isEqualTo("2026-01-01T00:00:01Z"); // earliest
        assertThat(merged.endTime()).isEqualTo("2026-01-01T00:00:40Z");   // latest
        assertThat(merged.features()).extracting(FeatureFragment::displayName)
            .containsExactly("Checkout", "Payments");
        assertThat(merged.features().get(0).scenarios()).extracting(ScenarioFragment::testId)
            .containsExactly("t1", "t2"); // unioned across shards
    }

    @Test
    void fragmentJsonRoundTripsWholeTestFlow() {
        Map<String, WholeTestFlowContent> wtf = new LinkedHashMap<>();
        wtf.put("t1", new WholeTestFlowContent(
            "<div class=\"plantuml-browser iflow-diagram\" id=\"iflow-puml-whole-t1\" data-plantuml-z=\"AAA\"></div>",
            "<div class=\"iflow-flame\" data-flame-z=\"BBB\"></div>", 7));
        ReportFragment fragment = new ReportFragment("Run", null, null,
            List.of(new FeatureFragment("Checkout", List.of(
                new ScenarioFragment("a", "t1", ExecutionStatus.PASSED, 5, null, "@startuml\nx\n@enduml")))),
            wtf);

        ReportFragment back = FragmentJson.fromJson(FragmentJson.toJson(fragment));
        assertThat(back).isEqualTo(fragment);
        assertThat(back.wholeTestFlow().get("t1").spanCount()).isEqualTo(7);
        // Byte-lock: a sequence-only fragment never grows a wholeTestFlow key.
        assertThat(FragmentJson.toJson(new ReportFragment("R", null, null, List.of())))
            .doesNotContain("wholeTestFlow");
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
            List.of(new FeatureFragment("Checkout", List.of(
                new ScenarioFragment("a", "t1", ExecutionStatus.PASSED, 1, null, "@startuml\nA->B\n@enduml")))),
            Map.of("t1", c1));
        ReportFragment shard2 = new ReportFragment("Merged", null, null,
            List.of(new FeatureFragment("Checkout", List.of(
                new ScenarioFragment("b", "t2", ExecutionStatus.PASSED, 1, null, "@startuml\nC->D\n@enduml")))),
            Map.of("t2", c2));

        // Exercise the full transport path: serialize each shard to JSON, read back, merge, then render.
        ReportFragment merged = MergeableReportMerger.merge(List.of(
            FragmentJson.fromJson(FragmentJson.toJson(shard1)),
            FragmentJson.fromJson(FragmentJson.toJson(shard2))));
        assertThat(merged.wholeTestFlow()).containsOnlyKeys("t1", "t2");

        String html = MergeableReportRenderer.renderHtml(merged);

        // Both tests' pre-rendered whole-test-flow content is embedded verbatim (no re-resolution)...
        assertThat(html)
            .contains("id=\"iflow-whole-t1\"").contains("data-flame-z=\"F1\"")
            .contains("id=\"iflow-whole-t2\"").contains("data-flame-z=\"F2\"")
            // ...behind the multi-view Diagrams toolbar (sequence + activity + flame views)...
            .contains("diagram-view-seq").contains("diagram-view-activity").contains("diagram-view-flame")
            // ...with the flame render script emitted (internalFlowTracking on for the merge path).
            .contains("renderFlameChart");
    }

    @Test
    void rendererProducesHtmlWithEmbeddedDiagrams() {
        ReportFragment fragment = new ReportFragment("Merged Run", null, null,
            List.of(new FeatureFragment("Checkout", List.of(
                new ScenarioFragment("a", "t1", ExecutionStatus.PASSED, 0, null,
                    "@startuml\nAlice -> Bob\n@enduml")))));

        String html = MergeableReportRenderer.renderHtml(fragment);
        assertThat(html)
            .contains("<title>Merged Run</title>")
            .contains("<h1>Merged Run</h1>")
            .contains("class=\"plantuml-browser\"")
            .contains("id=\"puml-data\"");                     // .NET-parity gzip diagram island
        // The fragment's pre-computed PlantUML survives, gzip-compressed, in the island.
        assertThat(io.kronikol.report.PumlData.all(html)).contains("Alice -> Bob");
    }
}
