package io.kronikol.report.merge;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.diagram.json.Json;
import io.kronikol.report.flow.InternalFlowSegment;
import io.kronikol.report.flow.InternalFlowSpan;
import io.kronikol.report.model.CiEnvironment;
import io.kronikol.report.model.CiMetadata;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.InlineParameterValue;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import io.kronikol.report.model.StepParameter;
import io.kronikol.report.model.StepTextSegment;
import io.kronikol.report.model.TableRowType;
import io.kronikol.report.model.TabularParameterValue;
import io.kronikol.report.model.TabularParameterValue.TabularCell;
import io.kronikol.report.model.TabularParameterValue.TabularColumn;
import io.kronikol.report.model.TabularParameterValue.TabularRow;
import io.kronikol.report.model.VerificationStatus;
import io.kronikol.diagram.component.ComponentRelationship;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Round-trip fidelity for {@link RecordJson} over the trickiest fragment model types. */
class RecordJsonTest {

    private static <T> T roundTrip(T value, Class<T> type) {
        return RecordJson.fromTree(Json.parse(Json.write(RecordJson.toTree(value))), type);
    }

    @Test
    void richFeatureWithDeepStepDetail_roundTrips() {
        ScenarioStep subStep = ScenarioStep.builder("And", "a valid session", ExecutionStatus.PASSED)
            .durationMs(2).comments(List.of("note A", "note B")).build();
        StepParameter inline = StepParameter.inline("amount",
            new InlineParameterValue("42", "≈40", VerificationStatus.SUCCESS));
        StepParameter tabular = StepParameter.tabular("rows", new TabularParameterValue(
            List.of(new TabularColumn("k", true), new TabularColumn("v", false)),
            List.of(new TabularRow(TableRowType.MATCHING,
                List.of(new TabularCell("id", null, VerificationStatus.NOT_APPLICABLE),
                        new TabularCell("7", "7", VerificationStatus.SUCCESS)))),
            false));
        ScenarioStep step = ScenarioStep.builder("When", "the order is placed", ExecutionStatus.FAILED)
            .durationMs(20).subSteps(List.of(subStep))
            .attachments(List.of(new FileAttachment("shot.png", "att/shot.png")))
            .docString("a body").docStringMediaType("text/plain")
            .textSegments(List.of(StepTextSegment.literal("places "),
                StepTextSegment.param("amount", new InlineParameterValue("42", null, VerificationStatus.SUCCESS))))
            .parameters(List.of(inline, tabular)).build();
        Scenario scenario = Scenario.builder("places an order", "t1", ExecutionStatus.FAILED)
            .isHappyPath(false).durationMs(50).error("boom").errorStackTrace("at X\n  at Y")
            .labels(List.of("@smoke")).categories(List.of("checkout")).rule("Rule A")
            .exampleValues(Map.of("amount", "42")).steps(List.of(step)).build();
        Feature feature = new Feature("Checkout", List.of(scenario));

        assertThat(roundTrip(feature, Feature.class)).isEqualTo(feature);
    }

    @Test
    void componentRelationshipWithSet_roundTrips() {
        ComponentRelationship rel = new ComponentRelationship(
            "Test", "OrderService", "HTTP", new java.util.LinkedHashSet<>(List.of("GET", "POST")),
            5, 3, "HTTP");
        assertThat(roundTrip(rel, ComponentRelationship.class)).isEqualTo(rel);
    }

    @Test
    void internalFlowSegmentWithInstantAndDouble_roundTrips() {
        InternalFlowSegment segment = new InternalFlowSegment("t1", List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /o",
                Instant.parse("2024-01-15T10:00:00Z"), 100.5, "traceA"),
            new InternalFlowSpan("2", "1", "Db", null, "SELECT",
                Instant.parse("2024-01-15T10:00:00.010Z"), 40.0, "traceA")),
            "00000000-0000-0000-0000-000000000001", "request");
        assertThat(roundTrip(segment, InternalFlowSegment.class)).isEqualTo(segment);
    }

    @Test
    void ciMetadataWithEnum_roundTrips() {
        CiMetadata ci = new CiMetadata(CiEnvironment.GITHUB_ACTIONS, "42", "main", "abc123",
            "https://ci/run/42", "acme/app", "run-99");
        assertThat(roundTrip(ci, CiMetadata.class)).isEqualTo(ci);
    }

    @Test
    void nullsAndEmpties_roundTrip() {
        Scenario minimal = Scenario.builder("bare", "t9", ExecutionStatus.PASSED).build();
        assertThat(roundTrip(minimal, Scenario.class)).isEqualTo(minimal);
    }
}
