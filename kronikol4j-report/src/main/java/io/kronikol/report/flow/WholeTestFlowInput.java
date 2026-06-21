package io.kronikol.report.flow;

import java.util.List;
import java.util.Map;

/**
 * The whole-test-flow render input: the captured segments (keyed {@code iflow-test-{testId}}), the
 * chosen {@link WholeTestFlowVisualization}, and the optional per-test flame boundary markers. Mirrors
 * the {@code wholeTestSegments} / {@code wholeTestVisualization} / boundary-log inputs of the .NET
 * {@code GenerateHtmlReport}. {@link #NONE} disables the whole-test-flow views.
 *
 * @param segments             captured segments keyed {@code iflow-test-{testId}}
 * @param visualization        which views to render
 * @param boundaryMarkers      per-test flame boundary markers (keyed by raw {@code testId}); may be empty
 * @param internalFlowTracking whether the interactive internal-flow scripts/styles are emitted in the
 *                             head (mirrors the .NET {@code internalFlowTracking} gate — the flame-chart
 *                             render script is required for the flame views to paint client-side)
 */
public record WholeTestFlowInput(
    Map<String, InternalFlowSegment> segments, WholeTestFlowVisualization visualization,
    Map<String, List<InternalFlowRenderer.BoundaryMarker>> boundaryMarkers, boolean internalFlowTracking) {

    public static final WholeTestFlowInput NONE =
        new WholeTestFlowInput(Map.of(), WholeTestFlowVisualization.NONE, Map.of(), false);

    public WholeTestFlowInput {
        segments = segments == null ? Map.of() : Map.copyOf(segments);
        visualization = visualization == null ? WholeTestFlowVisualization.NONE : visualization;
        boundaryMarkers = boundaryMarkers == null ? Map.of() : Map.copyOf(boundaryMarkers);
    }

    /** True when any whole-test-flow view should be produced. */
    public boolean isActive() {
        return visualization != WholeTestFlowVisualization.NONE && !segments.isEmpty();
    }
}
