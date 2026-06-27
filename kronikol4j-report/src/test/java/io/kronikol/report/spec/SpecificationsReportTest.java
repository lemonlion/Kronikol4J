package io.kronikol.report.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.data.ReportDataFormat;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the Specifications report data generators + writer (the .NET specifications path). */
class SpecificationsReportTest {

    private static List<Feature> sample() {
        Scenario happy = Scenario.builder("Places an order", "s1", ExecutionStatus.PASSED)
            .isHappyPath(true).labels(List.of("smoke"))
            .steps(List.of(ScenarioStep.of("Given", "an order", ExecutionStatus.PASSED),
                ScenarioStep.of("When", "it is placed", ExecutionStatus.PASSED)))
            .build();
        Scenario edge = Scenario.builder("Edge: empty cart", "s2", ExecutionStatus.PASSED)
            .isHappyPath(false).build();
        // Feature names out of order to prove sorting.
        Feature zebra = new Feature("Zebra", List.of(edge), null, null, List.of());
        Feature checkout = new Feature("Checkout", List.of(edge, happy), "POST /orders", "Buying", List.of("api"));
        return List.of(zebra, checkout);
    }

    @Test
    void yamlOrdersFeaturesByNameAndScenariosHappyPathFirst() {
        String yaml = SpecificationsData.yaml(sample(), "Service Specifications");

        assertThat(yaml).startsWith("Title: Service Specifications\nFeatures:\n");
        // Checkout sorts before Zebra
        assertThat(yaml.indexOf("Feature: Checkout")).isLessThan(yaml.indexOf("Feature: Zebra"));
        // happy-path scenario before the edge scenario within Checkout
        assertThat(yaml.indexOf("Places an order")).isLessThan(yaml.indexOf("Edge = empty cart"));
        assertThat(yaml).contains("Endpoint: POST /orders").contains("Description: Buying");
        assertThat(yaml).contains("        Steps:\n          - Given an order\n          - When it is placed\n");
        assertThat(yaml).contains("Edge = empty cart"); // ": " sanitised to " = "
    }

    @Test
    void jsonHasTitleFeaturesAndTextOnlySteps() {
        String json = SpecificationsData.json(sample(), "Service Specifications");
        assertThat(json)
            .contains("\"title\": \"Service Specifications\"")
            .contains("\"name\": \"Checkout\"")
            .contains("\"isHappyPath\": true")
            .contains("\"steps\": [\n")
            .contains("\"Given an order\"");
        assertThat(json.indexOf("\"name\": \"Checkout\"")).isLessThan(json.indexOf("\"name\": \"Zebra\""));
    }

    @Test
    void xmlHasNestedStructure() {
        String xml = SpecificationsData.xml(sample(), "Service Specifications");
        assertThat(xml)
            .startsWith("<Specifications>\n  <Title>Service Specifications</Title>")
            .contains("<Feature>\n      <Name>Checkout</Name>")
            .contains("<Endpoint>POST /orders</Endpoint>")
            .contains("<IsHappyPath>true</IsHappyPath>")
            .contains("<Step>Given an order</Step>");
    }

    @Test
    void writeEmitsHtmlAndDataFiles(@TempDir Path dir) throws IOException {
        SpecificationsReport.Generated g = SpecificationsReport.write(
            dir, sample(), Map.of(), SpecificationsOptions.defaults());

        assertThat(g.htmlFile()).isNotNull();
        assertThat(g.dataFile()).isNotNull();
        assertThat(dir.resolve("Specifications.html")).exists();
        assertThat(dir.resolve("Specifications.yaml")).exists();
        assertThat(Files.readString(g.htmlFile()))
            .contains("<title>Service Specifications</title>")
            .contains("Places an order");
        assertThat(Files.readString(g.dataFile())).startsWith("Title: Service Specifications");
    }

    @Test
    void writeHonoursToggferAndFormat(@TempDir Path dir) throws IOException {
        SpecificationsReport.write(dir, sample(), Map.of(),
            SpecificationsOptions.defaults().withGenerateReport(false).withDataFormat(ReportDataFormat.JSON));

        assertThat(dir.resolve("Specifications.html")).doesNotExist();   // report disabled
        assertThat(dir.resolve("Specifications.json")).exists();         // JSON format
        assertThat(dir.resolve("Specifications.yaml")).doesNotExist();
    }
}
