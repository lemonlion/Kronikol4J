package io.kronikol.report.ci;

/**
 * The CI summary/artifact-publishing options (the subset of the .NET {@code ReportConfigurationOptions}
 * that drives {@link CiSummaryWriter} and {@link CiArtifactPublisher}). Bundled as one carrier so the
 * report's option surface stays manageable.
 *
 * @param writeCiSummary       when {@code true}, the markdown run summary is written to the detected CI
 *                             platform's summary channel (and to {@code CiSummary.md}). Default {@code false}.
 * @param maxCiSummaryDiagrams maximum number of diagrams included in the CI summary. Default {@code 10}.
 * @param publishCiArtifacts   when {@code true}, generated report files are published as CI artifacts.
 *                             Default {@code false}.
 * @param ciArtifactName       the artifact name. Default {@code "TestReports"}.
 * @param ciArtifactRetentionDays retention period in days. Default {@code 1}.
 */
public record CiPublishOptions(boolean writeCiSummary, int maxCiSummaryDiagrams, boolean publishCiArtifacts,
                               String ciArtifactName, int ciArtifactRetentionDays) {

    /** The all-default options (no CI summary, no artifact publishing). */
    public static final CiPublishOptions NONE = new CiPublishOptions(false, 10, false,
        CiArtifactPublisher.DEFAULT_ARTIFACT_NAME, CiArtifactPublisher.DEFAULT_RETENTION_DAYS);

    public CiPublishOptions {
        if (ciArtifactName == null || ciArtifactName.isBlank()) {
            ciArtifactName = CiArtifactPublisher.DEFAULT_ARTIFACT_NAME;
        }
        if (maxCiSummaryDiagrams < 0) {
            maxCiSummaryDiagrams = 0;
        }
    }
}
