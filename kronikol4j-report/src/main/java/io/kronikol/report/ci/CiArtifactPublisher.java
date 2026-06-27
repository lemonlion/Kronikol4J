package io.kronikol.report.ci;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Publishes generated report files as CI artifacts using platform-specific mechanisms (GitHub Actions
 * artifact-output commands or Azure DevOps artifact-upload logging commands). Java port of the .NET
 * {@code CiArtifactPublisher}.
 */
public final class CiArtifactPublisher {

    /** The .NET default artifact name. */
    public static final String DEFAULT_ARTIFACT_NAME = "TestReports";
    /** The .NET default retention period. */
    public static final int DEFAULT_RETENTION_DAYS = 1;

    private CiArtifactPublisher() {
    }

    /** Publishes {@code reportFilePaths} to the CI platform's artifact channel using the real environment. */
    public static void publish(List<String> reportFilePaths, CiEnvironment environment, String artifactName,
                               int retentionDays) {
        publish(reportFilePaths, environment, artifactName, retentionDays, System::getenv,
            CiArtifactPublisher::appendFile, System.out::println, p -> Files.exists(Path.of(p)));
    }

    /**
     * As {@link #publish(List, CiEnvironment, String, int)}, with injectable seams (env lookup,
     * file-append, stdout, file-exists) so the platform routing is unit-testable.
     */
    static void publish(List<String> reportFilePaths, CiEnvironment environment, String artifactName,
                        int retentionDays, Function<String, String> getEnvVar,
                        BiConsumer<String, String> appendFile, Consumer<String> writeLine,
                        Predicate<String> fileExists) {
        switch (environment) {
            case AZURE_DEV_OPS -> {
                for (String path : reportFilePaths) {
                    if (!fileExists.test(path)) {
                        continue;
                    }
                    writeLine.accept("##vso[artifact.upload containerfolder=" + artifactName
                        + ";artifactname=" + artifactName + "]" + path);
                }
            }
            case GITHUB_ACTIONS -> {
                String outputPath = getEnvVar.apply("GITHUB_OUTPUT");
                if (outputPath == null || outputPath.isEmpty()) {
                    return;
                }
                String reportsDir = reportFilePaths.isEmpty() ? "" : parentOf(reportFilePaths.get(0));
                appendFile.accept(outputPath, "reports-path=" + reportsDir + "\n");
                appendFile.accept(outputPath, "reports-retention-days=" + retentionDays + "\n");
            }
            case NONE -> {
                // not a known CI environment — nothing to publish
            }
        }
    }

    private static String parentOf(String path) {
        Path parent = Path.of(path).getParent();
        return parent == null ? "" : parent.toString();
    }

    private static void appendFile(String path, String content) {
        try {
            Files.writeString(Path.of(path), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
