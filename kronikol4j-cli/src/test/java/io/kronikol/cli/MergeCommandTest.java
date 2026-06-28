package io.kronikol.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.merge.FragmentJson;
import io.kronikol.report.merge.ReportFragment;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MergeCommandTest {

    private static void writeFragment(Path file, String feature, String scenario, String testId,
                                      String diagram) throws IOException {
        ReportFragment fragment = new ReportFragment("Run", null, null,
            List.of(new Feature(feature,
                List.of(Scenario.builder(scenario, testId, ExecutionStatus.PASSED).build()))),
            Map.of(testId, diagram));
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
    void defaultsTitleAndIgnoresFragmentCarriedTitleWhenNoFlagGiven(@TempDir Path dir) throws IOException {
        // The fragments carry the title "Run" (see writeFragment). .NET's MergeableReport has no title field,
        // so a merge without -t always renders the default "Test Run Report" — never a fragment-carried title.
        writeFragment(dir.resolve("shard1.json"), "Checkout", "succeeds", "t1", "@startuml\nA->B\n@enduml");
        Path output = dir.resolve("Combined.html");

        int code = MergeCommand.run(
            new String[] {dir.toString(), "-o", output.toString()},
            new PrintStream(new ByteArrayOutputStream()), System.err);

        assertThat(code).isZero();
        String html = Files.readString(output);
        assertThat(html)
            .contains("<title>Test Run Report</title>")
            .doesNotContain("<title>Run</title>");
    }

    @Test
    void reportsErrorWhenNoInputs() {
        assertThat(MergeCommand.run(new String[] {}, System.out, System.err)).isEqualTo(2);
    }
}
