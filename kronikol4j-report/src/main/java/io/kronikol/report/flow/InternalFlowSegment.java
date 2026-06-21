package io.kronikol.report.flow;

import java.util.List;

/**
 * A segment of captured internal flow for one test (the runtime-neutral projection of the .NET
 * {@code InternalFlowSegment}). For the whole-test-flow views the segment is keyed
 * {@code iflow-test-{testId}} and holds every span captured for that test.
 *
 * @param testId the owning test id (used to derive the diagram element ids)
 * @param spans  the captured spans (rendered as an activity diagram and/or flame chart)
 */
public record InternalFlowSegment(String testId, List<InternalFlowSpan> spans) {

    public InternalFlowSegment {
        spans = spans == null ? List.of() : List.copyOf(spans);
    }
}
