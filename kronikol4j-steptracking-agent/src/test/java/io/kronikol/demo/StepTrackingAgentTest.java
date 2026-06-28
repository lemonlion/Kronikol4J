package io.kronikol.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.ScenarioStep;
import io.kronikol.report.step.GivenStep;
import io.kronikol.report.step.Step;
import io.kronikol.report.step.StepCollector;
import io.kronikol.report.step.StepTrackingOptions;
import io.kronikol.report.step.WhenStep;
import io.kronikol.steptracking.agent.KronikolStepTrackingAgent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Lives in a "user" package ({@code io.kronikol.demo}). Verifies the step-tracking agent wraps step-annotated
 * methods with {@link StepCollector} calls — no source change beyond the annotation: keyword + text derivation,
 * pass/fail status, nested sub-steps, and async ({@link CompletableFuture}) completion.
 */
class StepTrackingAgentTest {

    private static final String TID = "step-agent-t1";

    @BeforeAll
    static void installAgent() {
        KronikolStepTrackingAgent.install();
    }

    @BeforeEach
    @AfterEach
    void reset() {
        StepCollector.clearSteps(TID);
        StepCollector.options(StepTrackingOptions.defaults());
        TestIdentityScope.clear();
    }

    @Test
    void wrapsAnnotatedMethodsAsStepsWithDerivedKeywordAndText() {
        try (var scope = TestIdentityScope.begin("Demo", TID)) {
            new Sample().givenAnOrderExists();
            new Sample().whenItIsPlaced();
        }

        List<ScenarioStep> steps = StepCollector.getSteps(TID);
        assertThat(steps).extracting(ScenarioStep::keyword).containsExactly("Given", "When");
        assertThat(steps).extracting(ScenarioStep::text)
            .containsExactly("an order exists", "It is placed"); // explicit value / humanised + keyword-stripped
        assertThat(steps).allMatch(s -> s.status() == ExecutionStatus.PASSED);
    }

    @Test
    void failingStepIsRecordedFailedAndStillThrows() {
        try (var scope = TestIdentityScope.begin("Demo", TID)) {
            assertThatThrownBy(() -> new Sample().whenItFails())
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");
        }

        List<ScenarioStep> steps = StepCollector.getSteps(TID);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void asyncStepCompletesWhenTheReturnedFutureResolves() {
        CompletableFuture<String> result;
        try (var scope = TestIdentityScope.begin("Demo", TID)) {
            result = new Sample().whenAsyncWorkRuns();
        }

        assertThat(result.join()).isEqualTo("ok"); // original value flows through the wrapper
        List<ScenarioStep> steps = StepCollector.getSteps(TID);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void nestedStepBecomesASubStep() {
        try (var scope = TestIdentityScope.begin("Demo", TID)) {
            new Sample().givenAComposite();
        }

        List<ScenarioStep> steps = StepCollector.getSteps(TID);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).text()).isEqualTo("a composite");
        assertThat(steps.get(0).subSteps()).extracting(ScenarioStep::text).contains("an order exists");
    }

    /** A "user" class — only the annotations are added; the agent does the wrapping. */
    static class Sample {
        @GivenStep("an order exists")
        void givenAnOrderExists() {
        }

        @WhenStep
        void whenItIsPlaced() {
        }

        @WhenStep
        void whenItFails() {
            throw new IllegalStateException("boom");
        }

        @WhenStep
        CompletableFuture<String> whenAsyncWorkRuns() {
            return CompletableFuture.completedFuture("ok");
        }

        @Step("a composite")
        void givenAComposite() {
            givenAnOrderExists(); // nested step → sub-step
        }
    }
}
