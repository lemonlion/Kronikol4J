package io.kronikol.steptracking.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.step.GivenStep;
import io.kronikol.report.step.Step;
import io.kronikol.report.step.WhenStep;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Pure-logic coverage for {@link StepText} — keyword/text resolution + the {@code HumanizeMethodName} port. */
class StepTextTest {

    @Test
    void humanizesPascalCaseAndUnderscoresToSentenceCase() {
        assertThat(StepText.humanize("whenItIsPlaced")).isEqualTo("When it is placed");
        assertThat(StepText.humanize("the_order_exists")).isEqualTo("The order exists");
        // .NET sentence-cases by lowercasing everything after the first char, so the acronym is not preserved.
        assertThat(StepText.humanize("HTTPClientConnects")).isEqualTo("Http client connects");
    }

    @Test
    void explicitValueWinsOverTheMethodName() throws Exception {
        StepText.StepInfo info = StepText.resolve(method("givenAnOrderExists"));
        assertThat(info.keyword()).isEqualTo("Given");
        assertThat(info.text()).isEqualTo("an order exists");
    }

    @Test
    void derivesTextFromTheMethodNameStrippingTheLeadingKeyword() throws Exception {
        StepText.StepInfo info = StepText.resolve(method("whenItIsPlaced"));
        assertThat(info.keyword()).isEqualTo("When");
        assertThat(info.text()).isEqualTo("It is placed"); // "When it is placed" → keyword stripped, re-cased
    }

    @Test
    void plainStepHasNoKeyword() throws Exception {
        StepText.StepInfo info = StepText.resolve(method("aCompositeThing"));
        assertThat(info.keyword()).isNull();
        assertThat(info.text()).isEqualTo("A composite thing");
    }

    @Test
    void nonStepMethodResolvesToNull() throws Exception {
        assertThat(StepText.resolve(method("notAStep"))).isNull();
    }

    private static Method method(String name) throws NoSuchMethodException {
        return Fixtures.class.getDeclaredMethod(name);
    }

    @SuppressWarnings("unused")
    static class Fixtures {
        @GivenStep("an order exists")
        void givenAnOrderExists() {
        }

        @WhenStep
        void whenItIsPlaced() {
        }

        @Step
        void aCompositeThing() {
        }

        void notAStep() {
        }
    }
}
