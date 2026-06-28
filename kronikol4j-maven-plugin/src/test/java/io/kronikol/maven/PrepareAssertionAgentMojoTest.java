package io.kronikol.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link PrepareAssertionAgentMojo} resolves the agent jar from {@code ${plugin.artifactMap}} and
 * sets the {@code argLine} project property (the Maven mirror of the Gradle auto-attach), honouring skip and
 * the missing-agent case.
 */
class PrepareAssertionAgentMojoTest {

    private static final String KEY = "io.github.lemonlion:kronikol4j-assertj-agent";

    private static Artifact agentArtifact(File jar) {
        Artifact a = new DefaultArtifact("io.github.lemonlion", "kronikol4j-assertj-agent", "0.1.25",
            "compile", "jar", null, new DefaultArtifactHandler("jar"));
        a.setFile(jar);
        return a;
    }

    private static PrepareAssertionAgentMojo mojo(MavenProject project, Map<String, Artifact> artifacts) {
        PrepareAssertionAgentMojo m = new PrepareAssertionAgentMojo();
        m.setProject(project);
        m.setPluginArtifactMap(artifacts);
        m.setAssertionAgentArtifact(KEY);
        m.setArgLineProperty("argLine");
        m.setSkipAssertionAgent(false);
        return m;
    }

    @Test
    void setsArgLineFromTheResolvedAgentJar(@org.junit.jupiter.api.io.TempDir File dir) {
        File jar = new File(dir, "agent.jar");
        MavenProject project = new MavenProject();
        mojo(project, Map.of(KEY, agentArtifact(jar))).execute();

        assertThat(project.getProperties().getProperty("argLine"))
            .isEqualTo("-javaagent:" + jar.getAbsolutePath() + " -Dnet.bytebuddy.experimental=true");
    }

    @Test
    void prependsToAnExistingArgLine(@org.junit.jupiter.api.io.TempDir File dir) {
        File jar = new File(dir, "agent.jar");
        MavenProject project = new MavenProject();
        project.getProperties().setProperty("argLine", "-Xmx256m");

        mojo(project, Map.of(KEY, agentArtifact(jar))).execute();

        assertThat(project.getProperties().getProperty("argLine"))
            .isEqualTo("-javaagent:" + jar.getAbsolutePath() + " -Dnet.bytebuddy.experimental=true -Xmx256m");
    }

    @Test
    void skipLeavesArgLineUntouched(@org.junit.jupiter.api.io.TempDir File dir) {
        MavenProject project = new MavenProject();
        project.getProperties().setProperty("argLine", "-Xmx256m");
        PrepareAssertionAgentMojo m = mojo(project, Map.of(KEY, agentArtifact(new File(dir, "agent.jar"))));
        m.setSkipAssertionAgent(true);

        m.execute();

        assertThat(project.getProperties().getProperty("argLine")).isEqualTo("-Xmx256m"); // unchanged
    }

    @Test
    void missingAgentArtifactIsANoOp() {
        MavenProject project = new MavenProject();
        mojo(project, Map.of()).execute(); // agent not on the plugin classpath

        assertThat(project.getProperties().getProperty("argLine")).isNull();
    }
}
