package io.kronikol.gradle;

import io.kronikol.cli.MergeCommand;
import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Merges the report fragments emitted by forked test JVMs into one HTML report, by delegating to the
 * {@link MergeCommand} (the same engine as the {@code kronikol4j merge} CLI).
 */
public abstract class KronikolReportTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getFragmentsDir();

    @OutputFile
    public abstract RegularFileProperty getOutputHtml();

    @Input
    public abstract Property<String> getTitle();

    @TaskAction
    public void generate() {
        File dir = getFragmentsDir().getAsFile().getOrNull();
        File out = getOutputHtml().getAsFile().get();
        if (dir == null || !dir.exists()) {
            getLogger().lifecycle("[Kronikol4J] no fragments to merge");
            return;
        }
        int code = MergeCommand.run(
            new String[] {dir.getAbsolutePath(), "-o", out.getAbsolutePath(), "-t", getTitle().get()},
            System.out, System.err);
        if (code != 0 && code != 3) { // 3 = no *.json fragments found, which is fine
            throw new GradleException("Kronikol4J report merge failed with exit code " + code);
        }
    }
}
