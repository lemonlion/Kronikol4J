package io.kronikol.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;

/**
 * Wires Kronikol4J into a Gradle build (plan §5.3): every {@code Test} task gets {@code
 * kronikol.run.dir} set (so forked JVMs emit report fragments there) and is finalized by the
 * {@code kronikolReport} task, which merges those fragments into one HTML report.
 *
 * <pre>plugins { id("io.kronikol.kronikol4j") }</pre>
 */
public class KronikolPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        KronikolExtension extension = project.getExtensions().create("kronikol", KronikolExtension.class);
        extension.getReportDir().convention(project.getLayout().getBuildDirectory().dir("kronikol-report"));
        extension.getTitle().convention("Test Run Report");

        Provider<Directory> fragmentsDir = project.getLayout().getBuildDirectory().dir("kronikol-fragments");

        project.getTasks().register("kronikolReport", KronikolReportTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Merges Kronikol4J test report fragments into one HTML report.");
            task.getFragmentsDir().set(fragmentsDir);
            task.getOutputHtml().set(extension.getReportDir().file("TestRunReport.html"));
            task.getTitle().set(extension.getTitle());
        });

        project.getTasks().withType(Test.class).configureEach(test -> {
            test.systemProperty("kronikol.run.dir", fragmentsDir.get().getAsFile().getAbsolutePath());
            test.finalizedBy("kronikolReport");
        });
    }
}
