package io.kronikol.report.merge;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.merge.ReportFragment.FeatureFragment;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import io.kronikol.report.model.ExecutionStatus;
import java.util.List;
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
    void rendererProducesHtmlWithEmbeddedDiagrams() {
        ReportFragment fragment = new ReportFragment("Merged Run", null, null,
            List.of(new FeatureFragment("Checkout", List.of(
                new ScenarioFragment("a", "t1", ExecutionStatus.PASSED, 0, null,
                    "@startuml\nAlice -> Bob\n@enduml")))));

        String html = MergeableReportRenderer.renderHtml(fragment);
        assertThat(html)
            .contains("<title>Merged Run</title>")
            .contains("class=\"plantuml-browser\"")
            .contains("id=\"kronikol-diagrams\"");
    }
}
