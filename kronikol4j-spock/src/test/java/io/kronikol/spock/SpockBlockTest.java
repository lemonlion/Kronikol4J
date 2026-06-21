package io.kronikol.spock;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.TestPhase;
import org.junit.jupiter.api.Test;

class SpockBlockTest {

    @Test
    void givenSetupAndWhereAreSetup() {
        assertThat(SpockBlock.forBlock("given", TestPhase.UNKNOWN)).isEqualTo(TestPhase.SETUP);
        assertThat(SpockBlock.forBlock("setup", TestPhase.UNKNOWN)).isEqualTo(TestPhase.SETUP);
        assertThat(SpockBlock.forBlock("where", TestPhase.ACTION)).isEqualTo(TestPhase.SETUP);
    }

    @Test
    void whenThenExpectAreAction() {
        assertThat(SpockBlock.forBlock("when", TestPhase.SETUP)).isEqualTo(TestPhase.ACTION);
        assertThat(SpockBlock.forBlock("then", TestPhase.SETUP)).isEqualTo(TestPhase.ACTION);
        assertThat(SpockBlock.forBlock("expect", TestPhase.SETUP)).isEqualTo(TestPhase.ACTION);
    }

    @Test
    void andCleanupAndUnknownInheritTheCurrentPhase() {
        assertThat(SpockBlock.forBlock("and", TestPhase.ACTION)).isEqualTo(TestPhase.ACTION);
        assertThat(SpockBlock.forBlock("cleanup", TestPhase.ACTION)).isEqualTo(TestPhase.ACTION);
        assertThat(SpockBlock.forBlock("*", TestPhase.SETUP)).isEqualTo(TestPhase.SETUP);
        assertThat(SpockBlock.forBlock(null, TestPhase.SETUP)).isEqualTo(TestPhase.SETUP);
        assertThat(SpockBlock.forBlock("  GIVEN  ", TestPhase.UNKNOWN)).isEqualTo(TestPhase.SETUP);
    }
}
