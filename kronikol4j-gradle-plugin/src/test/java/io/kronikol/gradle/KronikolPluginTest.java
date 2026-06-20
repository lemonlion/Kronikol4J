package io.kronikol.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testfixtures.ProjectBuilder;

class KronikolPluginTest {

    @org.junit.jupiter.api.Test
    void registersExtensionAndReportTask() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(KronikolPlugin.class);

        assertThat(project.getExtensions().findByName("kronikol")).isNotNull();
        assertThat(project.getTasks().findByName("kronikolReport")).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void configuresTestTasksWithTheRunDirSystemProperty() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(KronikolPlugin.class);

        Test test = (Test) project.getTasks().getByName("test");
        assertThat(test.getSystemProperties()).containsKey("kronikol.run.dir");
    }
}
