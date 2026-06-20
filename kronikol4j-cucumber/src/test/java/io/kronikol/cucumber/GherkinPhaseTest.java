package io.kronikol.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TestPhase;
import java.net.URI;
import org.junit.jupiter.api.Test;

class GherkinPhaseTest {

    @Test
    void givenIsSetupAndWhenThenAreAction() {
        assertThat(GherkinPhase.forKeyword("Given ", TestPhase.UNKNOWN)).isEqualTo(TestPhase.SETUP);
        assertThat(GherkinPhase.forKeyword("When", TestPhase.SETUP)).isEqualTo(TestPhase.ACTION);
        assertThat(GherkinPhase.forKeyword("Then", TestPhase.SETUP)).isEqualTo(TestPhase.ACTION);
    }

    @Test
    void andButAndStarInheritTheCurrentPhase() {
        assertThat(GherkinPhase.forKeyword("And", TestPhase.SETUP)).isEqualTo(TestPhase.SETUP);
        assertThat(GherkinPhase.forKeyword("But", TestPhase.ACTION)).isEqualTo(TestPhase.ACTION);
        assertThat(GherkinPhase.forKeyword("*", TestPhase.ACTION)).isEqualTo(TestPhase.ACTION);
        assertThat(GherkinPhase.forKeyword(null, TestPhase.SETUP)).isEqualTo(TestPhase.SETUP);
    }

    @Test
    void featureNameIsDerivedFromTheUri() {
        assertThat(KronikolCucumberPlugin.featureName(URI.create("classpath:features/checkout.feature")))
            .isEqualTo("checkout");
        assertThat(KronikolCucumberPlugin.featureName(null)).isEqualTo("Cucumber");
    }
}
