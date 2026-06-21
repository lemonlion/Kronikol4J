package io.kronikol.report.merge;

import io.kronikol.report.flow.WholeTestFlowContent;
import io.kronikol.report.merge.ReportFragment.FeatureFragment;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combines report fragments from multiple JVMs/runners into one (plan §5.1, the "Merging Parallel
 * Reports" feature). Features are grouped by display name (first-seen order); scenarios are unioned
 * by test id (first wins — deduped across shards); earliest start / latest end are reconciled.
 */
public final class MergeableReportMerger {

    private MergeableReportMerger() {
    }

    public static ReportFragment merge(List<ReportFragment> fragments) {
        Map<String, Map<String, ScenarioFragment>> byFeature = new LinkedHashMap<>();
        Map<String, WholeTestFlowContent> wholeTestFlow = new LinkedHashMap<>();
        String title = null;
        String start = null;
        String end = null;

        for (ReportFragment fragment : fragments) {
            if (title == null) {
                title = fragment.title();
            }
            start = minTime(start, fragment.startTime());
            end = maxTime(end, fragment.endTime());
            for (FeatureFragment feature : fragment.features()) {
                Map<String, ScenarioFragment> scenarios =
                    byFeature.computeIfAbsent(feature.displayName(), k -> new LinkedHashMap<>());
                for (ScenarioFragment scenario : feature.scenarios()) {
                    scenarios.putIfAbsent(scenario.testId(), scenario);
                }
            }
            wholeTestFlow.putAll(fragment.wholeTestFlow()); // ids unique across disjoint runners (.NET MergeMap)
        }

        List<FeatureFragment> merged = new ArrayList<>();
        byFeature.forEach((name, scenarios) ->
            merged.add(new FeatureFragment(name, new ArrayList<>(scenarios.values()))));
        return new ReportFragment(title, start, end, merged, wholeTestFlow);
    }

    private static String minTime(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) <= 0 ? a : b; // ISO-8601 sorts lexically
    }

    private static String maxTime(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }
}
