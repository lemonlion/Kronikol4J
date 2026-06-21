package io.kronikol.report.flow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates the interactive internal-flow popup data — the {@code window.__iflowSegments} script the
 * popup JavaScript reads — a port of the .NET {@code InternalFlowHtmlGenerator}. Each per-diagram
 * segment renders its activity diagram (or call tree) inline (its PlantUML gzipped into a
 * {@code data-plantuml-z} attribute) and, optionally, raw flame-chart data.
 */
public final class InternalFlowHtmlGenerator {

    private InternalFlowHtmlGenerator() {
    }

    /** {@code <script>window.__iflowConfig = { hasDataBehavior: '…' };</script>} (.NET
     *  {@code DiagramContextMenu.GetInternalFlowConfigScript}). */
    public static String getInternalFlowConfigScript(InternalFlowHasDataBehavior hasDataBehavior) {
        String value = hasDataBehavior == InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER
            ? "showLinkOnHover" : "showLink";
        return "<script>window.__iflowConfig = { hasDataBehavior: '" + value + "' };</script>";
    }

    /** {@code <script>window.__iflowSegments = {…};</script>} for the popup (.NET
     *  {@code GenerateSegmentDataScript} → {@code WrapSegmentData(BuildSegmentData(...))}). */
    public static String generateSegmentDataScript(
            Map<String, InternalFlowSegment> segments, InternalFlowDiagramStyle diagramStyle,
            boolean showFlameChart, InternalFlowFlameChartPosition flameChartPosition,
            InternalFlowNoDataBehavior noDataBehavior, InternalFlowSpanGranularity granularity,
            String[] configuredActivitySources, int totalSpansInStore) {
        Map<String, Object> data = buildSegmentData(segments, diagramStyle, showFlameChart,
            flameChartPosition, noDataBehavior, granularity, configuredActivitySources, totalSpansInStore);
        return "<script>window.__iflowSegments = " + CompactJson.write(data) + ";</script>";
    }

    /** The per-segment data map ({@code segmentKey → { title, content[, flameData] }} or
     *  {@code { message }}), mirroring .NET {@code BuildSegmentData}. */
    public static Map<String, Object> buildSegmentData(
            Map<String, InternalFlowSegment> segments, InternalFlowDiagramStyle diagramStyle,
            boolean showFlameChart, InternalFlowFlameChartPosition flameChartPosition,
            InternalFlowNoDataBehavior noDataBehavior, InternalFlowSpanGranularity granularity,
            String[] configuredActivitySources, int totalSpansInStore) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, InternalFlowSegment> e : segments.entrySet()) {
            InternalFlowSegment segment = e.getValue();
            if (!segment.spans().isEmpty()) {
                String mainContent = diagramStyle == InternalFlowDiagramStyle.CALL_TREE
                    ? InternalFlowRenderer.renderCallTree(segment)
                    : renderActivityDiagramInline(segment);
                String content = mainContent;
                InternalFlowRenderer.FlameChartData flameData = null;
                if (showFlameChart) {
                    flameData = InternalFlowRenderer.getFlameChartData(segment, 2000);
                    String flamePlaceholder = "<div class=\"iflow-flame\" data-diagram-type=\"flamechart\"></div>";
                    content = flameChartPosition == InternalFlowFlameChartPosition.UNDERNEATH
                        ? mainContent + "<hr style=\"margin:12px 0\">" + flamePlaceholder
                        : "<div class=\"iflow-toggle\"><button class=\"iflow-toggle-btn iflow-toggle-active\""
                            + " data-view=\"main\">Activity</button><button class=\"iflow-toggle-btn\""
                            + " data-view=\"flame\">Flame Chart</button></div>"
                            + "<div class=\"iflow-view iflow-view-main\">" + mainContent + "</div>"
                            + "<div class=\"iflow-view iflow-view-flame\" style=\"display:none\">"
                            + flamePlaceholder + "</div>";
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("title", "Internal Flow (" + segment.spans().size()
                    + " span" + (segment.spans().size() == 1 ? "" : "s") + ")");
                entry.put("content", content);
                if (flameData != null && !flameData.spans().isEmpty()) {
                    Map<String, Object> fd = new LinkedHashMap<>();
                    fd.put("s", flameData.sources());
                    fd.put("f", flameData.spans());
                    entry.put("flameData", fd);
                }
                data.put(e.getKey(), entry);
            } else {
                if (noDataBehavior == InternalFlowNoDataBehavior.HIDE_LINK) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("message",
                    buildEmptyDiagnosticMessage(totalSpansInStore, granularity, configuredActivitySources));
                data.put(e.getKey(), entry);
            }
        }
        return data;
    }

    /** The inline (popup) activity-diagram div: the PlantUML gzipped into a {@code data-plantuml-z}
     *  attribute (not the {@code puml-data} island). Mirrors the {@code diagramDataMap == null} branch. */
    static String renderActivityDiagramInline(InternalFlowSegment segment) {
        String plantuml = InternalFlowRenderer.renderActivityDiagram(segment);
        String boundary = segment.boundaryType() == null ? "" : segment.boundaryType().toLowerCase(Locale.ROOT);
        String id = "iflow-puml-" + segment.requestResponseId() + "-" + boundary;
        return "<div class=\"plantuml-browser iflow-diagram\" id=\"" + id + "\" data-plantuml-z=\""
            + GzipBase64.encode(plantuml) + "\" data-diagram-type=\"plantuml\"></div>";
    }

    private static String buildEmptyDiagnosticMessage(int totalSpansInStore,
            InternalFlowSpanGranularity granularity, String[] configuredActivitySources) {
        StringBuilder sb = new StringBuilder();
        sb.append("No internal activity captured for this segment.");
        sb.append("<br/><br/><details style=\"font-size:0.85em;color:#666\"><summary>Diagnostic info</summary><ul>");
        sb.append("<li>InternalFlowSpanStore: ").append(totalSpansInStore).append(" total span(s) globally</li>");
        sb.append("<li>Granularity: ").append(granularity).append("</li>");
        if (configuredActivitySources != null && configuredActivitySources.length > 0) {
            sb.append("<li>Configured activity sources: ")
                .append(String.join(", ", configuredActivitySources)).append("</li>");
        } else if (granularity == InternalFlowSpanGranularity.MANUAL) {
            sb.append("<li>⚠ Granularity is Manual but no InternalFlowActivitySources configured</li>");
        }
        sb.append("</ul><p>Common causes:</p><ul>");
        sb.append("<li>ActivityListener not registered for the expected source</li>");
        sb.append("<li>Activity.Stop() not called before InternalFlowSpanStore.Add()</li>");
        sb.append("<li>Wrong InternalFlowSpanGranularity for your setup</li>");
        sb.append("</ul></details>");
        return sb.toString();
    }
}
