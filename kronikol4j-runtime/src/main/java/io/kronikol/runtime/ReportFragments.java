package io.kronikol.runtime;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.component.ComponentDiagramGenerator;
import io.kronikol.diagram.component.ComponentRelationship;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.report.ReportOptions;
import io.kronikol.report.flow.WholeTestFlowVisualization;
import io.kronikol.report.merge.ReportFragment;
import io.kronikol.report.model.Feature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds this JVM's {@link ReportFragment} from the collected {@link RunResults} and tracked logs —
 * the enriched, mergeable unit a forked JVM emits for the build plugin / CLI to merge (plan §5.3). The
 * fragment carries the full feature/scenario/step model (so a merged report keeps every step, parameter
 * and attachment), each scenario's pre-computed diagram, and the aggregated component relationships (so
 * the merged report rebuilds the run-level component diagram). Whole-test-flow / internal-flow popup
 * content is included when the caller has captured spans (the runtime-integration boundary).
 */
public final class ReportFragments {

    private ReportFragments() {
    }

    public static ReportFragment fromRun(String title) {
        return fromRun(title, ReportOptions.defaults());
    }

    /** As {@link #fromRun(String)}, honouring the diagram colour options. */
    public static ReportFragment fromRun(String title, ReportOptions options) {
        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        Map<String, String> diagramByTestId = new HashMap<>();
        for (PlantUmlForTest p : PlantUmlCreator.create(logs, options.arrowColors(), options.participantColors())) {
            if (!p.diagrams().isEmpty()) {
                diagramByTestId.put(p.testId(), p.diagrams().get(0)); // one per test (client-side splitting)
            }
        }
        List<Feature> features = RunResults.toFeatures();
        List<ComponentRelationship> relationships = ComponentDiagramGenerator.extractRelationships(logs);
        return new ReportFragment(title, null, null, features, diagramByTestId, relationships,
            Map.of(), null, WholeTestFlowVisualization.NONE, Map.of());
    }
}
