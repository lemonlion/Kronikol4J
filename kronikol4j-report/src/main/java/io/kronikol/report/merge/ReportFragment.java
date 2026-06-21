package io.kronikol.report.merge;

import io.kronikol.report.flow.WholeTestFlowContent;
import io.kronikol.report.model.ExecutionStatus;
import java.util.List;
import java.util.Map;

/**
 * The enriched, mergeable representation of one JVM/runner's results (plan §5). Each scenario carries
 * its own pre-computed PlantUML diagram, so merging combines finished fragments without re-rendering
 * raw logs (the .NET {@code GenerateMergeableData} model). Timestamps are ISO-8601 strings (or {@code
 * null}) so lexical min/max gives earliest-start / latest-end.
 *
 * <p>{@code wholeTestFlow} carries each test's pre-rendered internal-flow content (activity-diagram +
 * flame HTML + span count), keyed by test id — the .NET {@code MergeableReport.WholeTestFlow}
 * dictionary. It is fed to the renderer as {@code precomputedWholeTestContent} so the merged report
 * shows each shard's whole-test-flow without re-resolving from raw spans. Empty for sequence-only shards.
 */
public record ReportFragment(String title, String startTime, String endTime, List<FeatureFragment> features,
                             Map<String, WholeTestFlowContent> wholeTestFlow) {

    public ReportFragment {
        features = List.copyOf(features);
        wholeTestFlow = wholeTestFlow == null ? Map.of() : Map.copyOf(wholeTestFlow);
    }

    /** A sequence-only fragment (no captured whole-test-flow content). */
    public ReportFragment(String title, String startTime, String endTime, List<FeatureFragment> features) {
        this(title, startTime, endTime, features, Map.of());
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
