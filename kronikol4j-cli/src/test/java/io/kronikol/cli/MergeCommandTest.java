package io.kronikol.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.merge.ReportFragment;
import io.kronikol.report.merge.ReportFragment.FeatureFragment;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import io.kronikol.report.model.ExecutionStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MergeCommandTest {

    private static void writeFragment(Path file, String feature, String scenario, String testId,
                                      String diagram) throws IOException {
        ReportFragment fragment = new ReportFragment("Run", null, null,
            List.of(new FeatureFragment(feature, List.of(
                new ScenarioFragment(scenario, testId, ExecutionStatus.PASSED, 0, null, diagram)))));
        Files.writeString(file, FragmentJson.toJson(fragment), StandardCharsets.UTF_8);
    }

    @Test
    void mergesShardFragmentsIntoOneHtmlReport(@TempDir Path dir) throws IOException {
        writeFragment(dir.resolve("shard1.json"), "Checkout", "succeeds", "t1", "@startuml\nA->B\n@enduml");
        writeFragment(dir.resolve("shard2.json"), "Payments", "charges", "t2", "@startuml\nC->D\n@enduml");
        Path output = dir.resolve("Combined.html");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int code = MergeCommand.run(
            new String[] {dir.toString(), "-o", output.toString(), "-t", "Nightly Build"},
            new PrintStream(out), System.err);

        assertThat(code).isZero();
        assertThat(output).exists();
        String html = Files.readString(output);
        assertThat(html)
            .contains("<title>Nightly Build</title>")
            .contains("Checkout")
            .contains("Payments")
            .contains("succeeds")
            .contains("charges");
        assertThat(out.toString()).contains("Merged 2 fragment(s)");
    }

    @Test
    void reportsErrorWhenNoInputs() {
        assertThat(MergeCommand.run(new String[] {}, System.out, System.err)).isEqualTo(2);
    }
}
