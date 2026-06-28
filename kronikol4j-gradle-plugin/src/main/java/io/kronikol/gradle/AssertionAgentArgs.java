package io.kronikol.gradle;

import java.io.File;
import java.util.List;

/**
 * Computes the JVM arguments that attach the Kronikol4J assertion-tracking agent to a test JVM — the
 * build-time-weaving auto-wiring (so users don't pass {@code -javaagent} by hand). Pure logic, so the
 * decision (what args, when) is unit-tested independently of the Gradle wiring that resolves the agent jar.
 */
final class AssertionAgentArgs {

    private AssertionAgentArgs() {
    }

    /**
     * The JVM args to attach the agent: empty when disabled or the jar is unavailable, else the
     * {@code -javaagent} plus the ByteBuddy experimental flag (needed to instrument classes whose generic
     * signatures reference modern JDK types — matching the assertion-agent module's own test config).
     */
    static List<String> compute(boolean enabled, File agentJar) {
        if (!enabled || agentJar == null) {
            return List.of();
        }
        return List.of(
            "-javaagent:" + agentJar.getAbsolutePath(),
            "-Dnet.bytebuddy.experimental=true");
    }
}
