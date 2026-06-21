package io.kronikol.report.flow;

import java.time.Instant;

/**
 * A single captured span within an {@link InternalFlowSegment} — the runtime-neutral projection of the
 * .NET {@code System.Diagnostics.Activity} fields the internal-flow renderers actually read.
 *
 * <p>The .NET capture side produces {@code Activity} objects (and the Java capture side would produce
 * OpenTelemetry spans); neither is cross-runtime byte-parity-able. What <em>is</em> portable is this
 * deterministic projection — {@code spanId}/{@code parentSpanId} build the call tree, {@code sourceName}
 * is the swimlane, {@code displayName}/{@code operationName} the node label, and
 * {@code startTime}/{@code durationMs} drive ordering, the {@code (Nms)} suffix and flame offsets.
 *
 * @param spanId        the span's id (tree key); only the parent/child linkage is rendered, never the id
 * @param parentSpanId  the parent span's id, or null for a root span
 * @param sourceName    the activity source name (the PlantUML swimlane / flame source); "" → "Unknown"
 * @param displayName   the span's display name; when null the {@code operationName} is used as the label
 * @param operationName the span's operation name (label fallback)
 * @param startTime     the span start instant (UTC), used for deterministic ordering and flame offsets
 * @param durationMs    the span duration in milliseconds
 */
public record InternalFlowSpan(
    String spanId, String parentSpanId, String sourceName, String displayName, String operationName,
    Instant startTime, double durationMs) {

    /** The rendered label: {@code displayName} when present, else {@code operationName}. */
    public String label() {
        return displayName != null ? displayName : operationName;
    }

    /** The swimlane / flame source: the source name, or "Unknown" when blank. */
    public String sourceOrUnknown() {
        return sourceName == null || sourceName.isEmpty() ? "Unknown" : sourceName;
    }
}
