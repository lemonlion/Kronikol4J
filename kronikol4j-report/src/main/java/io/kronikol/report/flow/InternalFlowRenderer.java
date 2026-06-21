package io.kronikol.report.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders {@link InternalFlowSegment}s as PlantUML activity diagrams (and, later, flame-chart data),
 * a port of the .NET {@code InternalFlowRenderer}.
 *
 * <p>Line endings: the .NET renderer uses {@code StringBuilder.AppendLine}, i.e. {@code Environment
 * .NewLine}, and the resulting PlantUML is gzip-compressed into the report's {@code puml-data} island
 * <em>before</em> the report's {@code ReplaceLineEndings("\n")} runs — so the decoded payload carries
 * the capture host's native newline ({@code \r\n} on the Windows parity host). {@link #NL} mirrors that.
 */
public final class InternalFlowRenderer {

    /** Mirrors .NET {@code Environment.NewLine} on the Windows parity-capture host. */
    static final String NL = "\r\n";

    private InternalFlowRenderer() {
    }

    /** A node in the span tree (mirrors the .NET {@code SpanNode}). */
    static final class SpanNode {
        final InternalFlowSpan span;
        final List<SpanNode> children = new ArrayList<>();

        SpanNode(InternalFlowSpan span) {
            this.span = span;
        }
    }

    /** Renders the segment as a single PlantUML activity diagram (empty string when no spans). */
    public static String renderActivityDiagram(InternalFlowSegment segment) {
        if (segment.spans().isEmpty()) {
            return "";
        }
        List<SpanNode> roots = buildSpanTree(segment.spans());
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NL);
        sb.append("skinparam ActivityBackgroundColor #f0f4ff").append(NL);
        sb.append("skinparam ActivityBorderColor #666").append(NL);
        sb.append("skinparam SwimlaneBorderColor #ccc").append(NL);

        String[] currentSwimlane = {""};
        for (SpanNode root : roots) {
            renderActivityNode(sb, root, currentSwimlane);
        }
        sb.append("@enduml").append(NL);
        return sb.toString();
    }

    /**
     * Renders the segment as one or more PlantUML activity diagrams. A segment of ≤
     * {@code maxSpansPerBatch} spans yields a single diagram with no title; larger segments are split
     * into batches (each root + its descendants kept together), capped at 3 batches with a
     * {@code title Part i of N} line.
     */
    public static List<String> renderActivityDiagramBatched(InternalFlowSegment segment, int maxSpansPerBatch) {
        if (segment.spans().isEmpty()) {
            return List.of();
        }
        List<SpanNode> roots = buildSpanTree(segment.spans());
        if (segment.spans().size() <= maxSpansPerBatch) {
            return List.of(renderActivityDiagram(segment));
        }

        List<List<SpanNode>> batches = new ArrayList<>();
        List<SpanNode> currentBatch = new ArrayList<>();
        int currentCount = 0;
        for (SpanNode root : roots) {
            int nodeCount = countNodes(root);
            if (!currentBatch.isEmpty() && currentCount + nodeCount > maxSpansPerBatch) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentCount = 0;
            }
            currentBatch.add(root);
            currentCount += nodeCount;
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        final int maxBatches = 3;
        int totalBatches = batches.size();
        boolean truncated = totalBatches > maxBatches;
        int renderCount = truncated ? maxBatches : totalBatches;

        List<String> results = new ArrayList<>(renderCount);
        for (int i = 0; i < renderCount; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append("@startuml").append(NL);
            sb.append("skinparam ActivityBackgroundColor #f0f4ff").append(NL);
            sb.append("skinparam ActivityBorderColor #666").append(NL);
            sb.append("skinparam SwimlaneBorderColor #ccc").append(NL);
            String title = truncated
                ? "title Part " + (i + 1) + " of " + totalBatches + " (showing first " + maxBatches + ")"
                : "title Part " + (i + 1) + " of " + totalBatches;
            sb.append(title).append(NL);

            String[] currentSwimlane = {""};
            for (SpanNode root : batches.get(i)) {
                renderActivityNode(sb, root, currentSwimlane);
            }
            sb.append("@enduml").append(NL);
            results.add(sb.toString());
        }
        return results;
    }

    private static int countNodes(SpanNode node) {
        int count = 1;
        for (SpanNode child : node.children) {
            count += countNodes(child);
        }
        return count;
    }

    private static void renderActivityNode(StringBuilder sb, SpanNode node, String[] currentSwimlane) {
        String source = node.span.sourceOrUnknown();
        if (!source.equals(currentSwimlane[0])) {
            sb.append("|").append(escapePlantUml(source)).append("|").append(NL);
            currentSwimlane[0] = source;
        }
        String label = escapePlantUml(node.span.label());
        double duration = node.span.durationMs();
        sb.append(duration >= 1
            ? ":" + label + " (" + f0(duration) + "ms);"
            : ":" + label + ";").append(NL);

        for (SpanNode child : node.children) {
            renderActivityNode(sb, child, currentSwimlane);
        }
    }

    /**
     * Builds the span forest: spans link to parents by id (first span wins on duplicate ids), spans
     * whose parent is absent become roots, and roots + each node's children are ordered by start time.
     */
    static List<SpanNode> buildSpanTree(List<InternalFlowSpan> spans) {
        Map<String, SpanNode> nodesById = new LinkedHashMap<>();
        for (InternalFlowSpan span : spans) {
            nodesById.putIfAbsent(span.spanId(), new SpanNode(span));
        }
        List<SpanNode> roots = new ArrayList<>();
        for (SpanNode node : nodesById.values()) {
            String parentId = node.span.parentSpanId();
            SpanNode parent = parentId == null ? null : nodesById.get(parentId);
            if (parent != null) {
                parent.children.add(node);
            } else {
                roots.add(node);
            }
        }
        Comparator<SpanNode> byStart = Comparator.comparing(n -> n.span.startTime());
        roots.sort(byStart);
        for (SpanNode node : nodesById.values()) {
            node.children.sort(byStart);
        }
        return roots;
    }

    private static String escapePlantUml(String text) {
        return text.replace("|", "\\|").replace(";", "\\;");
    }

    /** Mirrors .NET {@code double.ToString("F0")} for the duration suffix. */
    private static String f0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }
}
