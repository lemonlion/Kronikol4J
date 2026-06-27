package io.kronikol.report.ci;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Writes CI summary markdown to the appropriate CI platform mechanism (GitHub Actions step summary or
 * Azure DevOps build output). Java port of the .NET {@code CiSummaryWriter}.
 */
public final class CiSummaryWriter {

    private CiSummaryWriter() {
    }

    /** Writes {@code markdown} to the CI platform's summary channel using the real environment. */
    public static void write(String markdown, CiEnvironment environment) {
        write(markdown, environment, System::getenv, CiSummaryWriter::appendFile, System.out::println);
    }

    /**
     * As {@link #write(String, CiEnvironment)}, with injectable seams (env lookup, file-append, stdout)
     * so the platform routing can be unit-tested without touching the real environment.
     */
    static void write(String markdown, CiEnvironment environment, Function<String, String> getEnvVar,
                      BiConsumer<String, String> appendFile, Consumer<String> writeLine) {
        switch (environment) {
            case GITHUB_ACTIONS -> {
                String summaryPath = getEnvVar.apply("GITHUB_STEP_SUMMARY");
                if (summaryPath == null || summaryPath.isEmpty()) {
                    return;
                }
                appendFile.accept(summaryPath, markdown);
            }
            case AZURE_DEV_OPS -> {
                // No Math.random()/UUID-at-call seam concern: the temp name is opaque to the assertion;
                // tests inject appendFile/writeLine and assert the ##vso command + that content was written.
                String tempPath = System.getProperty("java.io.tmpdir") + "/ci-summary-" + tempToken() + ".md";
                appendFile.accept(tempPath, markdown);
                writeLine.accept("##vso[task.uploadsummary]" + tempPath);
            }
            case NONE -> {
                // not a known CI environment — nothing to write
            }
        }
    }

    private static String tempToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
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
