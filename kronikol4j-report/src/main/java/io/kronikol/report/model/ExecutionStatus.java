package io.kronikol.report.model;

/** Outcome of a scenario. */
public enum ExecutionStatus {
    PASSED,
    FAILED,
    SKIPPED,
    INCONCLUSIVE;

    /** The .NET {@code ExecutionResult.ToString()} form used in report-data output (e.g. {@code "Passed"}). */
    public String displayName() {
        return switch (this) {
            case PASSED -> "Passed";
            case FAILED -> "Failed";
            case SKIPPED -> "Skipped";
            case INCONCLUSIVE -> "Bypassed"; // .NET's nearest ExecutionResult
        };
    }
}
