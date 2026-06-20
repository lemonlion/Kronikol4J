package io.kronikol.report.merge;

import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.merge.ReportFragment.FeatureFragment;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link ReportFragment} (typically the merged one) to the interactive HTML report,
 * reusing each scenario's pre-computed diagram. The .NET {@code MergeableReportRenderer} analog.
 */
public final class MergeableReportRenderer {

    private MergeableReportRenderer() {
    }

    public static String renderHtml(ReportFragment fragment) {
        List<Feature> features = new ArrayList<>();
        Map<String, String> diagramByTestId = new HashMap<>();
        for (FeatureFragment ff : fragment.features()) {
            List<Scenario> scenarios = new ArrayList<>();
            for (ScenarioFragment s : ff.scenarios()) {
                scenarios.add(new Scenario(s.name(), s.testId(), s.status(), s.durationMs(), s.error()));
                if (s.diagram() != null) {
                    diagramByTestId.put(s.testId(), s.diagram());
                }
            }
            features.add(new Feature(ff.displayName(), scenarios));
        }
        String title = fragment.title() == null ? "Test Run Report" : fragment.title();
        return HtmlReportGenerator.renderHtml(features, diagramByTestId, title);
    }
}
