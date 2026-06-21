package io.kronikol.report.flow;

import java.util.List;

/**
 * A segment of captured internal flow (the runtime-neutral projection of the .NET
 * {@code InternalFlowSegment}). For the whole-test-flow views the segment is keyed
 * {@code iflow-test-{testId}} and holds every span captured for that test. For the per-diagram popup it
 * is keyed {@code iflow-{requestResponseId}} and its activity-diagram element id is
 * {@code iflow-puml-{requestResponseId}-{boundaryType}}.
 *
 * @param testId            the owning test id (the whole-test activity id is {@code iflow-puml-whole-{testId}})
 * @param spans             the captured spans (rendered as an activity diagram, call tree, and/or flame chart)
 * @param requestResponseId the popup segment's request/response id; null for whole-test segments
 * @param boundaryType      the popup segment's boundary type (e.g. {@code "request"}); null for whole-test
 */
public record InternalFlowSegment(
    String testId, List<InternalFlowSpan> spans, String requestResponseId, String boundaryType) {

    public InternalFlowSegment {
        spans = spans == null ? List.of() : List.copyOf(spans);
    }

    /** Whole-test segment (no per-diagram request/boundary id). */
    public InternalFlowSegment(String testId, List<InternalFlowSpan> spans) {
        this(testId, spans, null, null);
    }
}
