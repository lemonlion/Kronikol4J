package io.kronikol.report.model;

/**
 * A single test case in the report. The {@code testId} links it to the tracked interactions whose
 * diagram is rendered beneath it.
 */
public record Scenario(String name, String testId, ExecutionStatus status, long durationMs, String error) {

    public static Scenario passed(String name, String testId) {
        return new Scenario(name, testId, ExecutionStatus.PASSED, 0, null);
    }
}
