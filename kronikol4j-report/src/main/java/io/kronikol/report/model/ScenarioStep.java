package io.kronikol.report.model;

import java.util.List;

/**
 * A single step within a scenario (Given/When/Then…), mirroring the .NET {@code ScenarioStep}:
 * keyword, text, status, duration, nested sub-steps, attachments, comments and an optional doc-string
 * (with media type). The richer .NET fields (inline/tabular/tree parameters, structured text segments)
 * are added incrementally as the report renderer ports them.
 *
 * @param durationMs the step's duration in milliseconds, or {@code null} if not timed
 */
public record ScenarioStep(String keyword, String text, ExecutionStatus status, Long durationMs,
                           List<ScenarioStep> subSteps, List<FileAttachment> attachments,
                           List<String> comments, String docString, String docStringMediaType,
                           List<StepTextSegment> textSegments) {

    public ScenarioStep {
        subSteps = subSteps == null ? List.of() : List.copyOf(subSteps);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        comments = comments == null ? List.of() : List.copyOf(comments);
        textSegments = textSegments == null ? List.of() : List.copyOf(textSegments);
    }

    /** Back-compatible step (keyword/text/status/duration/sub-steps/attachments; no comments/doc-string). */
    public ScenarioStep(String keyword, String text, ExecutionStatus status, Long durationMs,
                        List<ScenarioStep> subSteps, List<FileAttachment> attachments) {
        this(keyword, text, status, durationMs, subSteps, attachments, List.of(), null, null, List.of());
    }

    /** A leaf step with a keyword, text, and status. */
    public static ScenarioStep of(String keyword, String text, ExecutionStatus status) {
        return new ScenarioStep(keyword, text, status, null, List.of(), List.of());
    }

    public static Builder builder(String keyword, String text, ExecutionStatus status) {
        return new Builder(keyword, text, status);
    }

    /** Fluent builder for richly-populated steps (comments, doc-strings, sub-steps, attachments). */
    public static final class Builder {
        private final String keyword;
        private final String text;
        private final ExecutionStatus status;
        private Long durationMs;
        private List<ScenarioStep> subSteps = List.of();
        private List<FileAttachment> attachments = List.of();
        private List<String> comments = List.of();
        private String docString;
        private String docStringMediaType;
        private List<StepTextSegment> textSegments = List.of();

        private Builder(String keyword, String text, ExecutionStatus status) {
            this.keyword = keyword;
            this.text = text;
            this.status = status;
        }

        public Builder durationMs(long v) {
            this.durationMs = v;
            return this;
        }

        public Builder subSteps(List<ScenarioStep> v) {
            this.subSteps = v;
            return this;
        }

        public Builder attachments(List<FileAttachment> v) {
            this.attachments = v;
            return this;
        }

        public Builder comments(List<String> v) {
            this.comments = v;
            return this;
        }

        public Builder docString(String v) {
            this.docString = v;
            return this;
        }

        public Builder docStringMediaType(String v) {
            this.docStringMediaType = v;
            return this;
        }

        public Builder textSegments(List<StepTextSegment> v) {
            this.textSegments = v;
            return this;
        }

        public ScenarioStep build() {
            return new ScenarioStep(keyword, text, status, durationMs, subSteps, attachments,
                comments, docString, docStringMediaType, textSegments);
        }
    }
}
