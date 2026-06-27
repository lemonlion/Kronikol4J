package io.kronikol.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import io.kronikol.runtime.ReportFinalizer;
import io.kronikol.runtime.RunResults;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the {@link KronikolReportMojo} merge goal (the Maven mirror of the Gradle report task). */
class KronikolReportMojoTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RunResults.clear();
        RequestResponseLogger.clear();
    }

    @Test
    void mergesFragmentsIntoOneHtmlReport(@TempDir Path dir) throws Exception {
        Path fragmentsDir = dir.resolve("fragments");
        // A forked JVM would write this fragment; here we produce a real one.
        RunResults.record("Checkout", Scenario.builder("Checkout succeeds", "t1", ExecutionStatus.PASSED).build());
        Files.createDirectories(fragmentsDir);
        ReportFinalizer.writeFragment(fragmentsDir, "fragment-1.json", "ignored");

        Path output = dir.resolve("out/TestRunReport.html");
        KronikolReportMojo mojo = new KronikolReportMojo();
        mojo.setFragmentsDir(fragmentsDir.toFile());
        mojo.setOutputHtml(output.toFile());
        mojo.setTitle("Maven Run");

        mojo.execute();

        assertThat(output).exists();
        assertThat(Files.readString(output)).contains("Checkout succeeds").contains("Maven Run");
    }

    @Test
    void missingFragmentsDirIsANoOp(@TempDir Path dir) {
        KronikolReportMojo mojo = new KronikolReportMojo();
        mojo.setFragmentsDir(dir.resolve("does-not-exist").toFile());
        mojo.setOutputHtml(dir.resolve("out.html").toFile());
        mojo.setTitle("X");
        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void emptyFragmentsDirIsANoOp(@TempDir Path dir) throws IOException {
        Path empty = Files.createDirectories(dir.resolve("empty"));
        KronikolReportMojo mojo = new KronikolReportMojo();
        mojo.setFragmentsDir(empty.toFile());
        mojo.setOutputHtml(dir.resolve("out.html").toFile());
        mojo.setTitle("X");
        assertThatCode(mojo::execute).doesNotThrowAnyException(); // MergeCommand exit 3 = no fragments
    }

    @Test
    void pluginDescriptorIsPackagedAndVersionFiltered() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/maven/plugin.xml")) {
            assertThat(in).as("plugin.xml on the classpath").isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml)
                .contains("<goal>report</goal>")
                .contains("io.kronikol.maven.KronikolReportMojo")
                .contains("<goalPrefix>kronikol4j</goalPrefix>")
                .doesNotContain("@version@"); // the token was replaced by processResources
            assertThat(xml).containsPattern("<version>\\d+\\.\\d+\\.\\d+.*</version>");
        }
    }
}
