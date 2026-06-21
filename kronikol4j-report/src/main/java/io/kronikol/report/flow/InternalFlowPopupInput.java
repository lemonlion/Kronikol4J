package io.kronikol.report.flow;

import java.util.Map;

/**
 * The interactive internal-flow popup input: the per-diagram segments (keyed {@code iflow-{requestId}})
 * and the popup rendering options, mirroring the .NET {@code GenerateSegmentDataScript} parameters plus
 * the {@code InternalFlowHasDataBehavior} config. When {@link #internalFlowTracking} is on, the report
 * head emits the {@code window.__iflowConfig} + {@code window.__iflowSegments} scripts (and the
 * interactive-flow assets). {@link #NONE} disables the popup.
 */
public record InternalFlowPopupInput(
    Map<String, InternalFlowSegment> perDiagramSegments, InternalFlowDiagramStyle diagramStyle,
    boolean showFlameChart, InternalFlowFlameChartPosition flameChartPosition,
    InternalFlowNoDataBehavior noDataBehavior, InternalFlowSpanGranularity granularity,
    String[] configuredActivitySources, int totalSpansInStore,
    InternalFlowHasDataBehavior hasDataBehavior, boolean internalFlowTracking) {

    public static final InternalFlowPopupInput NONE = new InternalFlowPopupInput(
        Map.of(), InternalFlowDiagramStyle.ACTIVITY_DIAGRAM, false,
        InternalFlowFlameChartPosition.BEHIND_WITH_TOGGLE, InternalFlowNoDataBehavior.HIDE_LINK,
        InternalFlowSpanGranularity.AUTO_INSTRUMENTATION, null, 0,
        InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER, false);

    public InternalFlowPopupInput {
        perDiagramSegments = perDiagramSegments == null ? Map.of() : Map.copyOf(perDiagramSegments);
    }

    /** The {@code window.__iflowConfig} + {@code window.__iflowSegments} scripts, or "" when inactive. */
    public String dataScript() {
        if (!internalFlowTracking) {
            return "";
        }
        return InternalFlowHtmlGenerator.getInternalFlowConfigScript(hasDataBehavior)
            + InternalFlowHtmlGenerator.generateSegmentDataScript(perDiagramSegments, diagramStyle,
                showFlameChart, flameChartPosition, noDataBehavior, granularity, configuredActivitySources,
                totalSpansInStore);
    }
}
