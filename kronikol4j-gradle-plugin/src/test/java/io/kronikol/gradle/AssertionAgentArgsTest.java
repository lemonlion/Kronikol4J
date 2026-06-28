package io.kronikol.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;

/** Verifies the pure {@link AssertionAgentArgs} arg computation (the build-time-weaving decision logic). */
class AssertionAgentArgsTest {

    @Test
    void disabledYieldsNoArgs() {
        assertThat(AssertionAgentArgs.compute(false, new File("agent.jar"))).isEmpty();
    }

    @Test
    void enabledWithoutJarYieldsNoArgs() {
        assertThat(AssertionAgentArgs.compute(true, null)).isEmpty();
    }

    @Test
    void enabledYieldsJavaagentAndByteBuddyFlag() {
        File jar = new File("path/to/kronikol4j-assertj-agent.jar");
        assertThat(AssertionAgentArgs.compute(true, jar)).containsExactly(
            "-javaagent:" + jar.getAbsolutePath(),
            "-Dnet.bytebuddy.experimental=true");
    }
}
