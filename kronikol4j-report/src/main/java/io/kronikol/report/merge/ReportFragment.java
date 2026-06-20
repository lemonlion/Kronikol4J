package io.kronikol.report.merge;

import io.kronikol.report.model.ExecutionStatus;
import java.util.List;

/**
 * The enriched, mergeable representation of one JVM/runner's results (plan §5). Each scenario carries
 * its own pre-computed PlantUML diagram, so merging combines finished fragments without re-rendering
 * raw logs (the .NET {@code GenerateMergeableData} model). Timestamps are ISO-8601 strings (or {@code
 * null}) so lexical min/max gives earliest-start / latest-end.
 */
public record ReportFragment(String title, String startTime, String endTime, List<FeatureFragment> features) {

    public ReportFragment {
        features = List.copyOf(features);
    }

    public record FeatureFragment(String displayName, List<ScenarioFragment> scenarios) {
        public FeatureFragment {
            scenarios = List.copyOf(scenarios);
        }
    }

    public record ScenarioFragment(String name, String testId, ExecutionStatus status,
                                   long durationMs, String error, String diagram) {
    }
}
