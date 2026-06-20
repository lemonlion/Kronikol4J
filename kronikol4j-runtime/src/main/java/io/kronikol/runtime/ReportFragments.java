package io.kronikol.runtime;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.diagram.model.PlantUmlForTest;
import io.kronikol.diagram.plantuml.PlantUmlCreator;
import io.kronikol.report.merge.ReportFragment;
import io.kronikol.report.merge.ReportFragment.FeatureFragment;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds this JVM's {@link ReportFragment} from the collected {@link RunResults} and tracked logs —
 * the enriched, mergeable unit a forked JVM emits for the build plugin / CLI to merge (plan §5.3).
 * Each scenario carries its pre-computed diagram, so merging never re-renders raw logs.
 */
public final class ReportFragments {

    private ReportFragments() {
    }

    public static ReportFragment fromRun(String title) {
        Map<String, String> diagramByTestId = new HashMap<>();
        for (PlantUmlForTest p : PlantUmlCreator.create(RequestResponseLogger.getAllLogs())) {
            if (!p.diagrams().isEmpty()) {
                diagramByTestId.put(p.testId(), p.diagrams().get(0));
            }
        }

        List<FeatureFragment> features = new ArrayList<>();
        for (Feature feature : RunResults.toFeatures()) {
            List<ScenarioFragment> scenarios = new ArrayList<>();
            for (Scenario s : feature.scenarios()) {
                scenarios.add(new ScenarioFragment(s.name(), s.testId(), s.status(), s.durationMs(),
                    s.error(), diagramByTestId.get(s.testId())));
            }
            features.add(new FeatureFragment(feature.displayName(), scenarios));
        }
        return new ReportFragment(title, null, null, features);
    }
}
