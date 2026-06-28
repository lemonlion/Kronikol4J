package io.kronikol.maven;

import java.io.File;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Auto-attaches the Kronikol4J assertion-tracking agent to the forked test JVM — the Maven mirror of the
 * Gradle plugin's assertion-agent auto-wiring (so users don't pass {@code -javaagent} by hand). Bound to the
 * {@code initialize} phase, it resolves the agent jar from the plugin's own dependencies and prepends
 * {@code -javaagent:<jar> -Dnet.bytebuddy.experimental=true} to the {@code argLine} project property that
 * Surefire/Failsafe read — the same pattern as {@code jacoco:prepare-agent}.
 *
 * <p>Declare the assertion agent as a dependency of this plugin in the consuming POM (so it lands in
 * {@code ${plugin.artifactMap}}); the default coordinates are {@code io.github.lemonlion:kronikol4j-assertj-agent}.
 * Disable with {@code -Dkronikol.skipAssertionAgent=true}.
 */
@Mojo(name = "prepare-assertion-agent", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class PrepareAssertionAgentMojo extends AbstractMojo {

    /** The agent artifact key ({@code groupId:artifactId}) to resolve from the plugin's dependencies. */
    @Parameter(property = "kronikol.assertionAgentArtifact",
        defaultValue = "io.github.lemonlion:kronikol4j-assertj-agent")
    private String assertionAgentArtifact;

    /** When {@code true}, skip attaching the agent (leaves {@code argLine} untouched). */
    @Parameter(property = "kronikol.skipAssertionAgent", defaultValue = "false")
    private boolean skipAssertionAgent;

    /** The {@code argLine} property name Surefire/Failsafe read (overridable for custom forks). */
    @Parameter(property = "kronikol.argLineProperty", defaultValue = "argLine")
    private String argLineProperty;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${plugin.artifactMap}", readonly = true, required = true)
    private Map<String, Artifact> pluginArtifactMap;

    @Override
    public void execute() {
        if (skipAssertionAgent) {
            getLog().info("[Kronikol4J] assertion agent attachment skipped");
            return;
        }
        Artifact agent = pluginArtifactMap == null ? null : pluginArtifactMap.get(assertionAgentArtifact);
        File agentJar = agent == null ? null : agent.getFile();
        if (agentJar == null) {
            getLog().warn("[Kronikol4J] assertion agent '" + assertionAgentArtifact
                + "' not on the plugin classpath — add it as a plugin <dependency>; skipping -javaagent wiring");
            return;
        }
        String existing = project.getProperties().getProperty(argLineProperty);
        String updated = AssertionAgentArgLine.prepend(agentJar, existing);
        project.getProperties().setProperty(argLineProperty, updated);
        getLog().info("[Kronikol4J] assertion agent attached via " + argLineProperty + ": " + updated);
    }

    // --- package-private setters for unit testing (Maven injects the @Parameter fields by reflection) ---

    void setAssertionAgentArtifact(String value) {
        this.assertionAgentArtifact = value;
    }

    void setSkipAssertionAgent(boolean value) {
        this.skipAssertionAgent = value;
    }

    void setArgLineProperty(String value) {
        this.argLineProperty = value;
    }

    void setProject(MavenProject value) {
        this.project = value;
    }

    void setPluginArtifactMap(Map<String, Artifact> value) {
        this.pluginArtifactMap = value;
    }
}
