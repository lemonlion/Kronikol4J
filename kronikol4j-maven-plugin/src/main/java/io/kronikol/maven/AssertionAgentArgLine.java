package io.kronikol.maven;

import java.io.File;

/**
 * Computes the Surefire/Failsafe {@code argLine} value that attaches the Kronikol4J assertion-tracking agent
 * to the forked test JVM — the Maven mirror of the Gradle plugin's {@code AssertionAgentArgs} (build-time-
 * weaving auto-wiring, so users don't pass {@code -javaagent} by hand). Pure logic, unit-tested independently
 * of the Mojo that resolves the agent jar and sets the property.
 */
final class AssertionAgentArgLine {

    private AssertionAgentArgLine() {
    }

    /**
     * Prepends the agent's {@code -javaagent} (plus the ByteBuddy experimental flag the assertion agent needs)
     * to any {@code existingArgLine}, preserving the latter. Returns {@code existingArgLine} unchanged when
     * {@code agentJar} is {@code null} (the agent could not be resolved).
     */
    static String prepend(File agentJar, String existingArgLine) {
        String existing = existingArgLine == null ? "" : existingArgLine.trim();
        if (agentJar == null) {
            return existing.isEmpty() ? null : existing;
        }
        String agentArgs = "-javaagent:" + agentJar.getAbsolutePath() + " -Dnet.bytebuddy.experimental=true";
        return existing.isEmpty() ? agentArgs : agentArgs + " " + existing;
    }
}
