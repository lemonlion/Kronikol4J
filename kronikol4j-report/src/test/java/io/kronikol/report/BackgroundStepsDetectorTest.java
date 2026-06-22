package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guard-branch coverage for {@link BackgroundStepsDetector} — the no-extract cases the rendered
 * {@code report-background} golden does not reach. The happy extraction path is proven end-to-end by
 * {@code GoldenHtmlParityTest.backgroundDetectBrowserHtmlReport_*}.
 */
class BackgroundStepsDetectorTest {

    private static ScenarioStep step(String keyword, String text) {
        return new ScenarioStep(keyword, text, ExecutionStatus.PASSED, 0L, List.of(), List.of());
    }

    private static Scenario sc(String id, String rule, ScenarioStep... steps) {
        return Scenario.builder(id, id, ExecutionStatus.PASSED).rule(rule).steps(List.of(steps)).build();
    }

    @Test
    void extractsCommonGivenWhenPrefixWithinARule() {
        Scenario a = sc("a", "R", step("Given", "u"), step("When", "c"), step("Then", "x"));
        Scenario b = sc("b", "R", step("Given", "u"), step("When", "c"), step("Then", "y"));
        List<Scenario> out = BackgroundStepsDetector.detectAndExtract(List.of(a, b));
        assertThat(out.get(0).backgroundSteps()).extracting(ScenarioStep::text).containsExactly("u", "c");
        assertThat(out.get(0).steps()).extracting(ScenarioStep::text).containsExactly("x");
        assertThat(out.get(1).backgroundSteps()).extracting(ScenarioStep::text).containsExactly("u", "c");
        assertThat(out.get(1).steps()).extracting(ScenarioStep::text).containsExactly("y");
    }

    @Test
    void skipsWhenAScenarioOpensWithAndOrWhen() {
        Scenario a = sc("a", "R", step("When", "c"), step("Then", "x"));
        Scenario b = sc("b", "R", step("When", "c"), step("Then", "y"));
        List<Scenario> out = BackgroundStepsDetector.detectAndExtract(List.of(a, b));
        assertThat(out.get(0).backgroundSteps()).isEmpty();
        assertThat(out.get(0).steps()).hasSize(2); // untouched
    }

    @Test
    void skipsWhenTheRemainingStepReopensWithGiven() {
        // common prefix = 1 (Given u); the next step (Given v / Given w) re-opens → not a Background.
        Scenario a = sc("a", "R", step("Given", "u"), step("Given", "v"), step("Then", "x"));
        Scenario b = sc("b", "R", step("Given", "u"), step("Given", "w"), step("Then", "y"));
        assertThat(BackgroundStepsDetector.detectAndExtract(List.of(a, b)).get(0).backgroundSteps()).isEmpty();
    }

    @Test
    void doesNotGroupAcrossDifferentRules() {
        Scenario a = sc("a", "R1", step("Given", "u"), step("Then", "x"));
        Scenario b = sc("b", "R2", step("Given", "u"), step("Then", "y"));
        assertThat(BackgroundStepsDetector.detectAndExtract(List.of(a, b)).get(0).backgroundSteps()).isEmpty();
    }

    @Test
    void returnsInputUnchangedForFewerThanTwoScenarios() {
        Scenario a = sc("a", "R", step("Given", "u"), step("Then", "x"));
        List<Scenario> out = BackgroundStepsDetector.detectAndExtract(List.of(a));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).backgroundSteps()).isEmpty();
    }
}
