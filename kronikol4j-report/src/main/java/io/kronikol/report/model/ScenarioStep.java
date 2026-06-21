package io.kronikol.report.model;

import java.util.List;

/**
 * A single step within a scenario (Given/When/Then…), mirroring the .NET {@code ScenarioStep} for the
 * report-data serialization: keyword, text, status, duration, nested sub-steps, and attachments.
 * (The richer mergeable-report fields — inline parameters, text segments, doc-strings — are a
 * separate concern from the report-data output.)
 *
 * @param durationMs the step's duration in milliseconds, or {@code null} if not timed
 */
public record ScenarioStep(String keyword, String text, ExecutionStatus status, Long durationMs,
                           List<ScenarioStep> subSteps, List<FileAttachment> attachments) {

    public ScenarioStep {
        subSteps = subSteps == null ? List.of() : List.copyOf(subSteps);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /** A leaf step with a keyword, text, and status. */
    public static ScenarioStep of(String keyword, String text, ExecutionStatus status) {
        return new ScenarioStep(keyword, text, status, null, List.of(), List.of());
    }
}
