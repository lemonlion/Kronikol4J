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
 * @param precomputed          pre-rendered whole-test-flow content per test id (the .NET merge
 *                             {@code precomputedWholeTestContent} branch); {@code null} for the normal
 *                             capture-from-segments path. When non-null it is used <em>exclusively</em>
 *                             (a test absent from the map gets no whole-test-flow), mirroring
 *                             {@code ResolveWholeTestFlowContent}'s precomputed branch.
 */
public record WholeTestFlowInput(
    Map<String, InternalFlowSegment> segments, WholeTestFlowVisualization visualization,
    Map<String, List<InternalFlowRenderer.BoundaryMarker>> boundaryMarkers, boolean internalFlowTracking,
    Map<String, WholeTestFlowContent> precomputed) {

    public static final WholeTestFlowInput NONE =
        new WholeTestFlowInput(Map.of(), WholeTestFlowVisualization.NONE, Map.of(), false, null);

    public WholeTestFlowInput {
        segments = segments == null ? Map.of() : Map.copyOf(segments);
        visualization = visualization == null ? WholeTestFlowVisualization.NONE : visualization;
        boundaryMarkers = boundaryMarkers == null ? Map.of() : Map.copyOf(boundaryMarkers);
        precomputed = precomputed == null ? null : Map.copyOf(precomputed);
    }

    /** The capture-from-segments path (no precomputed content). */
    public WholeTestFlowInput(Map<String, InternalFlowSegment> segments, WholeTestFlowVisualization visualization,
                              Map<String, List<InternalFlowRenderer.BoundaryMarker>> boundaryMarkers,
                              boolean internalFlowTracking) {
        this(segments, visualization, boundaryMarkers, internalFlowTracking, null);
    }

    /**
     * The merge path: pre-rendered whole-test-flow content per test (the .NET
     * {@code precomputedWholeTestContent} branch). {@code internalFlowTracking} is on so the head emits
     * the flame-chart render script the embedded flame views need to paint.
     */
    public static WholeTestFlowInput precomputed(Map<String, WholeTestFlowContent> content) {
        return new WholeTestFlowInput(Map.of(), WholeTestFlowVisualization.BOTH, Map.of(), true, content);
    }

    /** True when any whole-test-flow view should be produced from captured segments. */
    public boolean isActive() {
        return visualization != WholeTestFlowVisualization.NONE && !segments.isEmpty();
    }
}
