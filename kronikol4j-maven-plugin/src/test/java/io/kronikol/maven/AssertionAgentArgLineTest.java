package io.kronikol.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;

/** Pure-logic coverage for {@link AssertionAgentArgLine#prepend}. */
class AssertionAgentArgLineTest {

    private static final File JAR = new File("/repo/kronikol4j-assertj-agent.jar");

    @Test
    void prependsJavaagentAndExperimentalFlagWhenNoExistingArgLine() {
        String result = AssertionAgentArgLine.prepend(JAR, null);
        assertThat(result)
            .isEqualTo("-javaagent:" + JAR.getAbsolutePath() + " -Dnet.bytebuddy.experimental=true");
    }

    @Test
    void preservesExistingArgLineAfterTheAgentArgs() {
        String result = AssertionAgentArgLine.prepend(JAR, "-Xmx512m -Dfoo=bar");
        assertThat(result)
            .isEqualTo("-javaagent:" + JAR.getAbsolutePath()
                + " -Dnet.bytebuddy.experimental=true -Xmx512m -Dfoo=bar");
    }

    @Test
    void blankExistingArgLineIsIgnored() {
        assertThat(AssertionAgentArgLine.prepend(JAR, "   "))
            .isEqualTo("-javaagent:" + JAR.getAbsolutePath() + " -Dnet.bytebuddy.experimental=true");
    }

    @Test
    void nullAgentJarLeavesArgLineUnchanged() {
        assertThat(AssertionAgentArgLine.prepend(null, "-Xmx512m")).isEqualTo("-Xmx512m");
        assertThat(AssertionAgentArgLine.prepend(null, null)).isNull();
    }
}
