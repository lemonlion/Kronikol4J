package io.kronikol.report.merge;

import io.kronikol.diagram.component.ComponentRelationship;
import io.kronikol.report.flow.InternalFlowSegment;
import io.kronikol.report.flow.WholeTestFlowContent;
import io.kronikol.report.flow.WholeTestFlowVisualization;
import io.kronikol.report.model.CiMetadata;
import io.kronikol.report.model.Feature;
import java.util.List;
import java.util.Map;

/**
 * The enriched, mergeable representation of one JVM/runner's results (plan §5) — the .NET
 * {@code MergeableReport} analog. Carries everything required to render (and merge) a full HTML report:
 * the complete {@link Feature}/{@code Scenario}/{@code ScenarioStep} model (steps, parameters,
 * attachments, rules — not a reduced summary), each test's pre-computed sequence/activity diagram, the
 * aggregated component-diagram relationships, the interactive internal-flow segments, the CI metadata,
 * and the whole-test-flow content. Timestamps are ISO-8601 strings (or {@code null}) so lexical min/max
 * gives earliest-start / latest-end.
 *
 * @param title                  report title (the merge {@code -t}); may be null
 * @param startTime              earliest start (ISO-8601) or null
 * @param endTime                latest end (ISO-8601) or null
 * @param features               the full feature/scenario/step model
 * @param diagramByTestId        each test's pre-computed PlantUML (sequence/activity), keyed by test id
 * @param componentRelationships aggregated component-diagram relationships (the run-level component diagram)
 * @param internalFlowSegments   per-diagram internal-flow segments for the interactive popup, keyed by id
 * @param ciMetadata             CI metadata for the run, or null
 * @param wholeTestVisualization the whole-test-flow visualization mode the source was rendered with
 * @param wholeTestFlow          pre-rendered whole-test-flow content (activity + flame HTML + span count)
 */
public record ReportFragment(
    String title, String startTime, String endTime,
    List<Feature> features,
    Map<String, String> diagramByTestId,
    List<ComponentRelationship> componentRelationships,
    Map<String, InternalFlowSegment> internalFlowSegments,
    CiMetadata ciMetadata,
    WholeTestFlowVisualization wholeTestVisualization,
    Map<String, WholeTestFlowContent> wholeTestFlow) {

    public ReportFragment {
        features = features == null ? List.of() : List.copyOf(features);
        diagramByTestId = diagramByTestId == null ? Map.of() : Map.copyOf(diagramByTestId);
        componentRelationships = componentRelationships == null ? List.of() : List.copyOf(componentRelationships);
        internalFlowSegments = internalFlowSegments == null ? Map.of() : Map.copyOf(internalFlowSegments);
        wholeTestVisualization = wholeTestVisualization == null
            ? WholeTestFlowVisualization.NONE : wholeTestVisualization;
        wholeTestFlow = wholeTestFlow == null ? Map.of() : Map.copyOf(wholeTestFlow);
    }

    /** A sequence-diagram-only shard (full features + per-test diagrams; no component/flow/CI extras). */
    public ReportFragment(String title, String startTime, String endTime, List<Feature> features,
                          Map<String, String> diagramByTestId) {
        this(title, startTime, endTime, features, diagramByTestId, List.of(), Map.of(), null,
            WholeTestFlowVisualization.NONE, Map.of());
    }

    /** A copy with a different report title, preserving all other content (the merge {@code -t} override). */
    public ReportFragment withTitle(String newTitle) {
        return new ReportFragment(newTitle, startTime, endTime, features, diagramByTestId,
            componentRelationships, internalFlowSegments, ciMetadata, wholeTestVisualization, wholeTestFlow);
    }
}
