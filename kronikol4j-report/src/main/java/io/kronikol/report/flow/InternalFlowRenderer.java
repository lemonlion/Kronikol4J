package io.kronikol.report.flow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    // ----------------------------------------------------------------------------- flame chart -----

    /** A boundary marker on the flame chart (an HTTP request timestamp + its label). */
    public record BoundaryMarker(String label, Instant timestamp) {
    }

    /**
     * Compact flame-chart data (.NET {@code FlameChartData}). Each {@code spans} row is
     * {@code [srcIdx(int), name(string), leftPct(double), widthPct(double), depth(int), durMs(int)]};
     * each optional {@code markers} row is {@code [leftPct(double), label(string)]}.
     */
    public record FlameChartData(List<String> sources, List<Object[]> spans, List<Object[]> markers) {
        static final FlameChartData EMPTY = new FlameChartData(List.of(), List.of(), null);

        boolean isEmpty() {
            return spans.isEmpty();
        }
    }

    /** Returns compact flame-chart data for the segment (a port of .NET {@code GetFlameChartData}). */
    public static FlameChartData getFlameChartData(InternalFlowSegment segment, int maxSpans) {
        if (segment.spans().isEmpty()) {
            return FlameChartData.EMPTY;
        }
        List<SpanNode> roots = buildSpanTree(segment.spans());
        Instant earliest = earliest(segment);
        double totalMs = totalMs(segment, earliest);

        List<String> sources = new ArrayList<>();
        Map<String, Integer> sourceIndex = new HashMap<>();
        List<Object[]> spans = new ArrayList<>();
        int[] spanCount = {0};
        for (SpanNode root : roots) {
            if (spanCount[0] >= maxSpans) {
                break;
            }
            flattenNode(root, 0, earliest, totalMs, sources, sourceIndex, spans, spanCount);
        }
        return new FlameChartData(sources, spans, null);
    }

    /** As {@link #getFlameChartData} but with boundary markers (.NET {@code GetFlameChartDataWithMarkers}). */
    public static FlameChartData getFlameChartDataWithMarkers(InternalFlowSegment segment,
                                                              List<BoundaryMarker> boundaryMarkers) {
        FlameChartData data = getFlameChartData(segment, 2000);
        if (data.isEmpty()) {
            return data;
        }
        Instant earliest = earliest(segment);
        double totalMs = totalMs(segment, earliest);

        List<Object[]> markers = new ArrayList<>();
        for (BoundaryMarker m : boundaryMarkers) {
            double offsetMs = offsetMs(earliest, m.timestamp());
            if (offsetMs >= 0 && offsetMs <= totalMs) {
                markers.add(new Object[] {round2(offsetMs / totalMs * 100), m.label()});
            }
        }
        return new FlameChartData(data.sources(), data.spans(), markers.isEmpty() ? null : markers);
    }

    private static void flattenNode(SpanNode node, int depth, Instant earliest, double totalMs,
                                    List<String> sources, Map<String, Integer> sourceIndex,
                                    List<Object[]> spans, int[] spanCount) {
        String source = node.span.sourceOrUnknown();
        Integer srcIdx = sourceIndex.get(source);
        if (srcIdx == null) {
            srcIdx = sources.size();
            sourceIndex.put(source, srcIdx);
            sources.add(source);
        }
        double offsetMs = offsetMs(earliest, node.span.startTime());
        double durationMs = node.span.durationMs();
        double leftPct = round2(offsetMs / totalMs * 100);
        double widthPct = round2(Math.max(durationMs / totalMs * 100, 0.5));
        int durMs = durationMs >= 1 ? (int) Math.rint(durationMs) : 0;
        spans.add(new Object[] {srcIdx, node.span.label(), leftPct, widthPct, depth, durMs});
        spanCount[0]++;
        for (SpanNode child : node.children) {
            flattenNode(child, depth + 1, earliest, totalMs, sources, sourceIndex, spans, spanCount);
        }
    }

    private static Instant earliest(InternalFlowSegment segment) {
        Instant earliest = segment.spans().get(0).startTime();
        for (InternalFlowSpan s : segment.spans()) {
            if (s.startTime().isBefore(earliest)) {
                earliest = s.startTime();
            }
        }
        return earliest;
    }

    /** {@code (latest - earliest)} in ms where {@code latest = max(start + duration)}; min 1 (mirrors .NET). */
    private static double totalMs(InternalFlowSegment segment, Instant earliest) {
        double totalMs = 0;
        for (InternalFlowSpan s : segment.spans()) {
            double end = offsetMs(earliest, s.startTime()) + s.durationMs();
            if (end > totalMs) {
                totalMs = end;
            }
        }
        return totalMs <= 0 ? 1 : totalMs;
    }

    private static double offsetMs(Instant earliest, Instant when) {
        return Duration.between(earliest, when).toNanos() / 1_000_000.0;
    }

    /** Mirrors .NET {@code Math.Round(x, 2)} (banker's / {@code MidpointRounding.ToEven}). */
    private static double round2(double x) {
        return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    /**
     * Serializes flame data as compact JSON (no whitespace), byte-identical to .NET's
     * {@code JsonSerializer.Serialize(new { s, f[, m] }, WriteIndented=false)}.
     */
    public static String flameJson(FlameChartData d) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"s\":[");
        for (int i = 0; i < d.sources().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendJsonString(sb, d.sources().get(i));
        }
        sb.append("],\"f\":[");
        appendRows(sb, d.spans());
        sb.append(']');
        if (d.markers() != null) {
            sb.append(",\"m\":[");
            appendRows(sb, d.markers());
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendRows(StringBuilder sb, List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[');
            Object[] row = rows.get(i);
            for (int j = 0; j < row.length; j++) {
                if (j > 0) {
                    sb.append(',');
                }
                appendCell(sb, row[j]);
            }
            sb.append(']');
        }
    }

    private static void appendCell(StringBuilder sb, Object cell) {
        if (cell instanceof String s) {
            appendJsonString(sb, s);
        } else if (cell instanceof Integer n) {
            sb.append(n.intValue());
        } else if (cell instanceof Double dv) {
            sb.append(formatJsonNumber(dv));
        } else {
            sb.append(cell);
        }
    }

    /** System.Text.Json number formatting for an already-rounded double: integers without a decimal
     *  point, otherwise the shortest decimal with trailing zeros stripped (50.0→"50", 33.30→"33.3"). */
    private static String formatJsonNumber(double r) {
        if (r == Math.rint(r) && !Double.isInfinite(r)) {
            return Long.toString((long) r);
        }
        return BigDecimal.valueOf(r).stripTrailingZeros().toPlainString();
    }

    /** Escapes a string exactly like System.Text.Json's default (HTML-safe) encoder. */
    private static void appendJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\u0022");
                case '&' -> sb.append("\\u0026");
                case '\'' -> sb.append("\\u0027");
                case '+' -> sb.append("\\u002B");
                case '<' -> sb.append("\\u003C");
                case '>' -> sb.append("\\u003E");
                case '`' -> sb.append("\\u0060");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\f' -> sb.append("\\f");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        sb.append("\\u").append(String.format(Locale.ROOT, "%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
