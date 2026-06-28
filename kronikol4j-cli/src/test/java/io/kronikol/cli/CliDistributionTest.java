package io.kronikol.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import io.kronikol.runtime.ReportFinalizer;
import io.kronikol.runtime.RunResults;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the runnable fat-jar distribution end-to-end: spawns {@code java -jar <fatjar>} and checks the
 * {@code merge} command works and the usage/exit-code contract holds. The fat-jar path is supplied by the
 * build via the {@code kronikol.cli.fatjar} system property (the {@code test} task depends on {@code fatJar}).
 */
class CliDistributionTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RunResults.clear();
        RequestResponseLogger.clear();
    }

    private static Path fatJar() {
        String path = System.getProperty("kronikol.cli.fatjar");
        assertThat(path).as("kronikol.cli.fatjar system property (set by the build)").isNotNull();
        Path jar = Path.of(path);
        assertThat(jar).as("built fat-jar").exists();
        return jar;
    }

    private static String javaExe() {
        String exe = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", exe).toString();
    }

    private record Result(int exitCode, String output) {
    }

    private static Result run(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(javaExe(), "-jar", fatJar().toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int code = process.waitFor();
        return new Result(code, output);
    }

    @Test
    void fatJarMergesFragmentsEndToEnd(@TempDir Path dir) throws Exception {
        Path fragments = Files.createDirectories(dir.resolve("fragments"));
        RunResults.record("Checkout", Scenario.builder("Checkout succeeds", "t1", ExecutionStatus.PASSED).build());
        ReportFinalizer.writeFragment(fragments, "fragment-1.json", "ignored");
        Path out = dir.resolve("TestRunReport.html");

        Result result = run("merge", fragments.toString(), "-o", out.toString(), "-t", "CLI Run");

        assertThat(result.exitCode()).as(result.output()).isEqualTo(0);
        assertThat(out).exists();
        assertThat(Files.readString(out)).contains("Checkout succeeds").contains("CLI Run");
    }

    @Test
    void noArgsPrintsUsageWithZeroExit() throws Exception {
        Result result = run();
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("Usage: kronikol4j").contains("merge");
    }

    @Test
    void unknownCommandExitsTwo() throws Exception {
        Result result = run("bogus");
        assertThat(result.exitCode()).isEqualTo(2);
    }

    @Test
    void mergeWithNoFragmentsDirExitsThree() throws Exception {
        Result result = run("merge", new File("definitely-missing-dir").getAbsolutePath());
        assertThat(result.exitCode()).isEqualTo(3); // no *.json fragments
    }
}
