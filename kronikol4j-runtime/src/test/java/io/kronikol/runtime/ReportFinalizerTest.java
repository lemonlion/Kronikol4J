package io.kronikol.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportFinalizerTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RunResults.clear();
        RequestResponseLogger.clear();
    }

    @Test
    void recordsScenariosGroupedByFeatureInOrder() {
        RunResults.record("Checkout", Scenario.passed("a", "1"));
        RunResults.record("Payments", Scenario.passed("b", "2"));
        RunResults.record("Checkout", Scenario.passed("c", "3"));

        List<Feature> features = RunResults.toFeatures();
        assertThat(features).extracting(Feature::displayName).containsExactly("Checkout", "Payments");
        assertThat(features.get(0).scenarios()).extracting(Scenario::name).containsExactly("a", "c");
    }

    @Test
    void finalizeReturnsNullWhenNothingTracked() throws IOException {
        assertThat(ReportFinalizer.finalizeRun(Path.of("unused"), "x")).isNull();
    }

    @Test
    void finalizeWritesReportFromCollectedResults(@TempDir Path dir) throws IOException {
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        var report = ReportFinalizer.finalizeRun(dir, "Run");
        assertThat(report).isNotNull();
        assertThat(Files.readString(report.htmlFile())).contains("Checkout succeeds").contains("1 passed");
    }

    @Test
    void forkedModeDetectedFromRunDirProperty() {
        assertThat(ReportFinalizer.isForkedMode()).isFalse();
        System.setProperty(ReportFinalizer.RUN_DIR_PROPERTY, "/tmp/run-123");
        try {
            assertThat(ReportFinalizer.isForkedMode()).isTrue();
        } finally {
            System.clearProperty(ReportFinalizer.RUN_DIR_PROPERTY);
        }
    }

    @Test
    void writesAnAtomicFragmentFromCollectedResults(@TempDir Path dir) throws IOException {
        RunResults.record("Checkout", Scenario.passed("Checkout succeeds", "t1"));

        Path file = ReportFinalizer.writeFragment(dir, "fragment-test.json", "Run");

        assertThat(file).exists();
        assertThat(dir.resolve("fragment-test.json.tmp")).doesNotExist(); // temp cleaned up
        var fragment = FragmentJson.fromJson(Files.readString(file));
        assertThat(fragment.features()).anySatisfy(f ->
            assertThat(f.scenarios()).extracting(ScenarioFragment::name).contains("Checkout succeeds"));
    }

    @Test
    void writeFragmentReturnsNullWhenNothingTracked(@TempDir Path dir) throws IOException {
        assertThat(ReportFinalizer.writeFragment(dir, "f.json", "Run")).isNull();
    }
}
