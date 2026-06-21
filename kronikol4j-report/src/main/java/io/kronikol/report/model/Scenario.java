package io.kronikol.report.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single test case in the report (the full report-data model, mirroring the .NET {@code Scenario}).
 * The first five components are the always-present core; the rest carry BDD / parameterized-test
 * metadata used by the report-data serializers ({@code stableId} is derived, not stored). The
 * {@code testId} links it to the tracked interactions whose diagram is rendered beneath it.
 *
 * <p>{@code exampleValues} preserves insertion order (it is emitted ordered in JSON) and keeps the
 * {@code null}-vs-empty distinction; the list fields default to empty. {@code exampleFlatValues} holds
 * the original (un-flattened) Gherkin example columns when a structured/flattened view is also shown —
 * it drives the report's flatten toggle and, like the .NET field, is HTML-only (never serialized).
 *
 * @param durationMs duration in milliseconds (0 if untimed)
 */
public record Scenario(
    String name, String testId, ExecutionStatus status, long durationMs, String error,
    boolean isHappyPath, String errorStackTrace, List<String> labels, List<String> categories,
    String rule, String outlineId, Map<String, String> exampleValues, Map<String, String> exampleFlatValues,
    String exampleDisplayName,
    List<FileAttachment> attachments, List<ScenarioStep> backgroundSteps, List<ScenarioStep> steps) {

    public Scenario {
        labels = labels == null ? List.of() : List.copyOf(labels);
        categories = categories == null ? List.of() : List.copyOf(categories);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        backgroundSteps = backgroundSteps == null ? List.of() : List.copyOf(backgroundSteps);
        steps = steps == null ? List.of() : List.copyOf(steps);
        exampleValues = exampleValues == null
            ? null : Collections.unmodifiableMap(new LinkedHashMap<>(exampleValues)); // ordered, null kept
        exampleFlatValues = exampleFlatValues == null
            ? null : Collections.unmodifiableMap(new LinkedHashMap<>(exampleFlatValues)); // ordered, null kept
    }

    /** The deterministic, cross-run stable id derived from the owning feature + this scenario. */
    public String stableId(String featureName) {
        return ScenarioStableId.compute(featureName, name, outlineId);
    }

    /** Back-compatible core scenario (no BDD / parameterized metadata). */
    public Scenario(String name, String testId, ExecutionStatus status, long durationMs, String error) {
        this(name, testId, status, durationMs, error, false, null, List.of(), List.of(),
            null, null, null, null, null, List.of(), List.of(), List.of());
    }

    public static Scenario passed(String name, String testId) {
        return new Scenario(name, testId, ExecutionStatus.PASSED, 0, null);
    }

    public static Builder builder(String name, String testId, ExecutionStatus status) {
        return new Builder(name, testId, status);
    }

    /** Fluent builder for richly-populated scenarios (BDD steps, examples, attachments, …). */
    public static final class Builder {
        private final String name;
        private final String testId;
        private final ExecutionStatus status;
        private long durationMs;
        private String error;
        private boolean isHappyPath;
        private String errorStackTrace;
        private List<String> labels = List.of();
        private List<String> categories = List.of();
        private String rule;
        private String outlineId;
        private Map<String, String> exampleValues;
        private Map<String, String> exampleFlatValues;
        private String exampleDisplayName;
        private List<FileAttachment> attachments = List.of();
        private List<ScenarioStep> backgroundSteps = List.of();
        private List<ScenarioStep> steps = List.of();

        private Builder(String name, String testId, ExecutionStatus status) {
            this.name = name;
            this.testId = testId;
            this.status = status;
        }

        public Builder durationMs(long v) { this.durationMs = v; return this; }
        public Builder error(String v) { this.error = v; return this; }
        public Builder isHappyPath(boolean v) { this.isHappyPath = v; return this; }
        public Builder errorStackTrace(String v) { this.errorStackTrace = v; return this; }
        public Builder labels(List<String> v) { this.labels = v; return this; }
        public Builder categories(List<String> v) { this.categories = v; return this; }
        public Builder rule(String v) { this.rule = v; return this; }
        public Builder outlineId(String v) { this.outlineId = v; return this; }
        public Builder exampleValues(Map<String, String> v) { this.exampleValues = v; return this; }
        public Builder exampleFlatValues(Map<String, String> v) { this.exampleFlatValues = v; return this; }
        public Builder exampleDisplayName(String v) { this.exampleDisplayName = v; return this; }
        public Builder attachments(List<FileAttachment> v) { this.attachments = v; return this; }
        public Builder backgroundSteps(List<ScenarioStep> v) { this.backgroundSteps = v; return this; }
        public Builder steps(List<ScenarioStep> v) { this.steps = v; return this; }

        public Scenario build() {
            return new Scenario(name, testId, status, durationMs, error, isHappyPath, errorStackTrace,
                labels, categories, rule, outlineId, exampleValues, exampleFlatValues, exampleDisplayName,
                attachments, backgroundSteps, steps);
        }
    }
}
