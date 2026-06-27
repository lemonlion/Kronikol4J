package io.kronikol.report.ci;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the CI publish machinery — {@link CiEnvironment} detection, {@link CiSummaryWriter} and
 * {@link CiArtifactPublisher} platform routing — via injectable seams (the same approach the .NET tests use).
 */
class CiPublishTest {

    // --- CiEnvironment.detect ---

    @Test
    void detectsGitHubActions() {
        assertThat(CiEnvironment.detect(name -> "GITHUB_ACTIONS".equals(name) ? "true" : null))
            .isEqualTo(CiEnvironment.GITHUB_ACTIONS);
    }

    @Test
    void detectsAzureDevOps() {
        assertThat(CiEnvironment.detect(name -> "TF_BUILD".equals(name) ? "True" : null))
            .isEqualTo(CiEnvironment.AZURE_DEV_OPS);
    }

    @Test
    void detectsNoneOutsideCi() {
        assertThat(CiEnvironment.detect(name -> null)).isEqualTo(CiEnvironment.NONE);
        assertThat(CiEnvironment.detect(name -> "")).isEqualTo(CiEnvironment.NONE); // blank ignored
    }

    // --- CiSummaryWriter ---

    @Test
    void summaryWriterAppendsToGitHubStepSummary() {
        Map<String, String> appended = new HashMap<>();
        List<String> lines = new ArrayList<>();
        CiSummaryWriter.write("# Summary", CiEnvironment.GITHUB_ACTIONS,
            name -> "GITHUB_STEP_SUMMARY".equals(name) ? "/tmp/step-summary" : null,
            appended::put, lines::add);
        assertThat(appended).containsEntry("/tmp/step-summary", "# Summary");
        assertThat(lines).isEmpty();
    }

    @Test
    void summaryWriterNoOpsWhenGitHubSummaryPathMissing() {
        Map<String, String> appended = new HashMap<>();
        CiSummaryWriter.write("# Summary", CiEnvironment.GITHUB_ACTIONS, name -> null, appended::put,
            l -> { });
        assertThat(appended).isEmpty();
    }

    @Test
    void summaryWriterEmitsAzureUploadCommand() {
        Map<String, String> appended = new HashMap<>();
        List<String> lines = new ArrayList<>();
        CiSummaryWriter.write("# Summary", CiEnvironment.AZURE_DEV_OPS, name -> null, appended::put, lines::add);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).startsWith("##vso[task.uploadsummary]");
        assertThat(appended).hasSize(1); // markdown written to the temp file referenced by the command
        assertThat(lines.get(0)).endsWith(appended.keySet().iterator().next());
    }

    @Test
    void summaryWriterNoOpsOutsideCi() {
        Map<String, String> appended = new HashMap<>();
        List<String> lines = new ArrayList<>();
        CiSummaryWriter.write("# Summary", CiEnvironment.NONE, name -> "x", appended::put, lines::add);
        assertThat(appended).isEmpty();
        assertThat(lines).isEmpty();
    }

    // --- CiArtifactPublisher ---

    @Test
    void publisherWritesGitHubOutputs() {
        Map<String, String> appended = new HashMap<>();
        List<String> appendedOrder = new ArrayList<>();
        CiArtifactPublisher.publish(List.of("/reports/TestRunReport.html", "/reports/TestRunReport.json"),
            CiEnvironment.GITHUB_ACTIONS, "MyArtifacts", 5,
            name -> "GITHUB_OUTPUT".equals(name) ? "/tmp/gh-output" : null,
            (path, content) -> { appended.merge(path, content, String::concat); appendedOrder.add(content); },
            l -> { }, p -> true);
        assertThat(appended.get("/tmp/gh-output"))
            .contains("reports-path=" + java.nio.file.Path.of("/reports") + "\n")
            .contains("reports-retention-days=5\n");
    }

    @Test
    void publisherNoOpsWhenGitHubOutputMissing() {
        Map<String, String> appended = new HashMap<>();
        CiArtifactPublisher.publish(List.of("/reports/a.html"), CiEnvironment.GITHUB_ACTIONS, "X", 1,
            name -> null, appended::put, l -> { }, p -> true);
        assertThat(appended).isEmpty();
    }

    @Test
    void publisherEmitsAzureUploadPerExistingFile() {
        List<String> lines = new ArrayList<>();
        CiArtifactPublisher.publish(List.of("/r/a.html", "/r/missing.json"), CiEnvironment.AZURE_DEV_OPS,
            "TestReports", 1, name -> null, (p, c) -> { }, lines::add,
            p -> p.equals("/r/a.html")); // only a.html exists
        assertThat(lines).containsExactly(
            "##vso[artifact.upload containerfolder=TestReports;artifactname=TestReports]/r/a.html");
    }

    // --- options carrier ---

    @Test
    void reportOptionsCarriesCiAndReadsSystemProperties() {
        assertThat(io.kronikol.report.ReportOptions.defaults().ci()).isEqualTo(CiPublishOptions.NONE);

        CiPublishOptions ci = new CiPublishOptions(true, 3, true, "Reports", 7);
        assertThat(io.kronikol.report.ReportOptions.defaults().withCi(ci).ci()).isEqualTo(ci);
        // survives an unrelated wither
        assertThat(io.kronikol.report.ReportOptions.defaults().withCi(ci).withArrowColors(true).ci())
            .isEqualTo(ci);

        Map<String, String> previous = new HashMap<>();
        Map<String, String> props = Map.of(
            io.kronikol.report.ReportOptions.WRITE_CI_SUMMARY_PROPERTY, "true",
            io.kronikol.report.ReportOptions.MAX_CI_SUMMARY_DIAGRAMS_PROPERTY, "4",
            io.kronikol.report.ReportOptions.PUBLISH_CI_ARTIFACTS_PROPERTY, "true",
            io.kronikol.report.ReportOptions.CI_ARTIFACT_NAME_PROPERTY, "MyReports",
            io.kronikol.report.ReportOptions.CI_ARTIFACT_RETENTION_DAYS_PROPERTY, "9");
        props.forEach((k, v) -> {
            previous.put(k, System.getProperty(k));
            System.setProperty(k, v);
        });
        try {
            CiPublishOptions fromProps = io.kronikol.report.ReportOptions.ciFromSystemProperties();
            assertThat(fromProps).isEqualTo(new CiPublishOptions(true, 4, true, "MyReports", 9));
        } finally {
            previous.forEach((k, v) -> {
                if (v == null) {
                    System.clearProperty(k);
                } else {
                    System.setProperty(k, v);
                }
            });
        }
    }

    @Test
    void ciPublishOptionsDefaultsAndNormalisation() {
        assertThat(CiPublishOptions.NONE.writeCiSummary()).isFalse();
        assertThat(CiPublishOptions.NONE.maxCiSummaryDiagrams()).isEqualTo(10);
        assertThat(CiPublishOptions.NONE.publishCiArtifacts()).isFalse();
        assertThat(CiPublishOptions.NONE.ciArtifactName()).isEqualTo("TestReports");
        assertThat(CiPublishOptions.NONE.ciArtifactRetentionDays()).isEqualTo(1);
        // blank name → default; negative max → 0
        CiPublishOptions normalised = new CiPublishOptions(true, -3, true, "  ", 2);
        assertThat(normalised.ciArtifactName()).isEqualTo("TestReports");
        assertThat(normalised.maxCiSummaryDiagrams()).isZero();
    }
}
