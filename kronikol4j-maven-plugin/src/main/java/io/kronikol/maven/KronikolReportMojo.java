package io.kronikol.maven;

import io.kronikol.cli.MergeCommand;
import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Merges the report fragments emitted by forked test JVMs into one HTML report — the Maven mirror of the
 * Gradle {@code kronikolReport} task. Delegates to the same {@link MergeCommand} engine as the
 * {@code kronikol4j merge} CLI. Bound to the {@code verify} phase by default.
 *
 * <p>Forked test JVMs emit fragments when {@code kronikol.run.dir} is set; configure that on Surefire/Failsafe
 * (e.g. {@code <systemPropertyVariables><kronikol.run.dir>${project.build.directory}/kronikol-fragments</…>})
 * pointing at {@link #fragmentsDir}.
 */
@Mojo(name = "report", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class KronikolReportMojo extends AbstractMojo {

    /** Directory the forked test JVMs wrote their fragments to. */
    @Parameter(property = "kronikol.fragmentsDir",
        defaultValue = "${project.build.directory}/kronikol-fragments")
    private File fragmentsDir;

    /** Where the merged HTML report is written. */
    @Parameter(property = "kronikol.outputHtml",
        defaultValue = "${project.build.directory}/kronikol-report/TestRunReport.html")
    private File outputHtml;

    /** The report title. */
    @Parameter(property = "kronikol.title", defaultValue = "Test Run Report")
    private String title;

    @Override
    public void execute() throws MojoExecutionException {
        if (fragmentsDir == null || !fragmentsDir.exists()) {
            getLog().info("[Kronikol4J] no fragments to merge");
            return;
        }
        File parent = outputHtml.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new MojoExecutionException("Cannot create report output directory " + parent);
        }
        int code = MergeCommand.run(
            new String[] {fragmentsDir.getAbsolutePath(), "-o", outputHtml.getAbsolutePath(), "-t", title},
            System.out, System.err);
        if (code != 0 && code != 3) { // 3 = no *.json fragments found, which is fine
            throw new MojoExecutionException("Kronikol4J report merge failed with exit code " + code);
        }
    }

    // --- package-private setters for unit testing (Maven injects the @Parameter fields by reflection) ---

    void setFragmentsDir(File fragmentsDir) {
        this.fragmentsDir = fragmentsDir;
    }

    void setOutputHtml(File outputHtml) {
        this.outputHtml = outputHtml;
    }

    void setTitle(String title) {
        this.title = title;
    }
}
