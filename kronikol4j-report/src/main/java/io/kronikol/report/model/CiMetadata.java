package io.kronikol.report.model;

/**
 * Metadata about the CI environment where a test run executed (.NET {@code CiMetadata}). Rendered into
 * the Test Execution Summary's {@code ci-metadata} table; every field but {@code provider} is optional.
 */
public record CiMetadata(
    CiEnvironment provider, String buildNumber, String branch, String commitSha,
    String pipelineUrl, String repository, String runId) {
}
