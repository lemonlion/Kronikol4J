package io.kronikol.report.step;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.ScenarioStep;
import io.kronikol.report.model.StepParameter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies the {@link StepCollector} runtime matches the .NET {@code StepCollector}: nesting, keyword
 *  sequencing, bypass, assertion sub-steps, attachments, delimiters and phase transitions. */
class StepCollectorTest {

    private static final String TID = "t1";

    @BeforeEach
    @AfterEach
    void reset() {
        StepCollector.clearSteps(TID);
        StepCollector.options(StepTrackingOptions.defaults());
        RequestResponseLogger.clear();
        TestPhaseContext.reset();
        TestIdentityScope.clear();
    }

    @Test
    void completedTopLevelStepBecomesAScenarioStep() {
        StepCollector.startStep(TID, "Given", "an order exists", null, null);
        StepCollector.completeStep(TID, true, null);

        List<ScenarioStep> steps = StepCollector.getSteps(TID);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).keyword()).isEqualTo("Given");
        assertThat(steps.get(0).text()).isEqualTo("an order exists");
        assertThat(steps.get(0).status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(steps.get(0).durationMs()).isNotNull();
    }

    @Test
    void repeatedKeywordSequencesToAnd() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        StepCollector.startStep(TID, "Given", "first", null, null);
        StepCollector.completeStep(TID, true, null);
        StepCollector.startStep(TID, "Given", "second", null, null);
        StepCollector.completeStep(TID, true, null);

        assertThat(StepCollector.getSteps(TID)).extracting(ScenarioStep::keyword)
            .containsExactly("Given", "And");
    }

    @Test
    void butWhenDisplaysAsBut() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        StepCollector.startStep(TID, "ButWhen", "something else happens", null, null);
        StepCollector.completeStep(TID, true, null);
        assertThat(StepCollector.getSteps(TID).get(0).keyword()).isEqualTo("But");
    }

    @Test
    void stepStartedWhileAnotherActiveBecomesASubStep() {
        StepCollector.startStep(TID, "When", "outer", null, null);
        StepCollector.startStep(TID, "And", "inner", null, null);
        StepCollector.completeStep(TID, true, null);   // completes inner
        StepCollector.completeStep(TID, true, null);   // completes outer

        List<ScenarioStep> steps = StepCollector.getSteps(TID);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).text()).isEqualTo("outer");
        assertThat(steps.get(0).subSteps()).extracting(ScenarioStep::text).containsExactly("inner");
    }

    @Test
    void bypassedStepIsInconclusiveWithReasonComment() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        StepCollector.startStep(TID, "When", "skipped step", null, null);
        StepCollector.bypassStep(TID, "feature flag off");

        ScenarioStep step = StepCollector.getSteps(TID).get(0);
        assertThat(step.status()).isEqualTo(ExecutionStatus.INCONCLUSIVE);
        assertThat(step.comments()).contains("Bypassed: feature flag off");
    }

    @Test
    void trackedAssertionBecomesASubStepWhenEnabled() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        StepCollector.startStep(TID, "Then", "verify", null, null);
        StepCollector.addAssertionSubStep(TID, "order.status == CONFIRMED", true);
        StepCollector.completeStep(TID, true, null);

        ScenarioStep step = StepCollector.getSteps(TID).get(0);
        assertThat(step.subSteps()).extracting(ScenarioStep::text).containsExactly("order.status == CONFIRMED");

        // disabled → no sub-step
        StepCollector.clearSteps(TID);
        StepCollector.options(StepCollector.options().withIncludeTrackedAssertionsInStepList(false));
        StepCollector.startStep(TID, "Then", "verify", null, null);
        StepCollector.addAssertionSubStep(TID, "x", true);
        StepCollector.completeStep(TID, true, null);
        assertThat(StepCollector.getSteps(TID).get(0).subSteps()).isEmpty();
    }

    @Test
    void attachmentsGoToTheActiveStepOrTheScenario() {
        StepCollector.addAttachment(TID, "/tmp/scenario.log", null);        // no active step → scenario
        StepCollector.startStep(TID, "Given", "s", null, null);
        StepCollector.addAttachment(TID, "/tmp/step.png", "shot");          // active step
        StepCollector.completeStep(TID, true, null);

        assertThat(StepCollector.getScenarioAttachments(TID))
            .extracting(io.kronikol.report.model.FileAttachment::name).containsExactly("scenario.log");
        assertThat(StepCollector.getSteps(TID).get(0).attachments())
            .extracting(io.kronikol.report.model.FileAttachment::name).containsExactly("shot");
    }

    @Test
    void whenTriggersActionPhaseTransition() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        StepCollector.startStep(TID, "Given", "setup", null, null);
        assertThat(TestPhaseContext.current()).isEqualTo(TestPhase.SETUP);
        StepCollector.startStep(TID, "When", "act", null, null);
        assertThat(TestPhaseContext.current()).isEqualTo(TestPhase.ACTION);
    }

    @Test
    void topLevelStepEmitsAStepDelimiterNote() {
        StepCollector.startStep(TID, "Given", "an order exists", null, null);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).anySatisfy(l -> assertThat(l.plantUml())
            .contains("stepDelimiter").contains("Given an order exists"));
    }

    @Test
    void inlineParametersAreCaptured() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        StepCollector.startStep(TID, "Given", "order id {id}",
            new String[] {"id"}, new Object[] {42});
        StepCollector.completeStep(TID, true, null);

        ScenarioStep step = StepCollector.getSteps(TID).get(0);
        assertThat(step.parameters()).hasSize(1);
        assertThat(step.parameters().get(0).name()).isEqualTo("id");
        assertThat(step.parameters().get(0).inlineValue().value()).isEqualTo("42");
    }

    @Test
    void tabularParameterDataBecomesATabularStepParameter() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        var columns = List.of(new io.kronikol.report.model.TabularParameterValue.TabularColumn("id", true));
        var rows = List.of(new io.kronikol.report.model.TabularParameterValue.TabularRow(
            io.kronikol.report.model.TableRowType.MATCHING,
            List.of(new io.kronikol.report.model.TabularParameterValue.TabularCell("1", null,
                io.kronikol.report.model.VerificationStatus.NOT_APPLICABLE))));
        TabularParameterData table = new TabularParameterData() {
            public List<io.kronikol.report.model.TabularParameterValue.TabularColumn> getColumns() {
                return columns;
            }

            public List<io.kronikol.report.model.TabularParameterValue.TabularRow> getRows() {
                return rows;
            }
        };

        StepCollector.startStep(TID, "Given", "rows", new String[] {"data"}, new Object[] {table});
        StepCollector.completeStep(TID, true, null);

        StepParameter param = StepCollector.getSteps(TID).get(0).parameters().get(0);
        assertThat(param.kind()).isEqualTo(StepParameter.Kind.TABULAR);
        assertThat(param.tabularValue().columns()).extracting(
            io.kronikol.report.model.TabularParameterValue.TabularColumn::name).containsExactly("id");
        assertThat(param.tabularValue().rows()).hasSize(1);
    }

    @Test
    void resolvesTestIdFromAmbientScope() {
        StepCollector.options(StepCollector.options().withShowStepDelimiters(false));
        try (var scope = TestIdentityScope.begin("MyTest", TID)) {
            StepCollector.startStep("Given", "ambient", null, null);
            StepCollector.completeStep(true, null);
        }
        assertThat(StepCollector.getSteps(TID)).extracting(ScenarioStep::text).containsExactly("ambient");
        assertThat(StepCollector.hasActiveStep(TID)).isFalse();
    }
}
