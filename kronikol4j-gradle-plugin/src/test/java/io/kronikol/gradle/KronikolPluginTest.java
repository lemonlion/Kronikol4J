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

    @org.junit.jupiter.api.Test
    void forwardsConfiguredReportOptionsAsSystemProperties() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(KronikolPlugin.class);

        KronikolExtension ext = project.getExtensions().getByType(KronikolExtension.class);
        ext.getArrowColors().set(true);
        ext.getPlantUmlTheme().set("cerulean");
        ext.getDataFormats().set(java.util.List.of("json", "yaml"));
        ext.getGenerateSchema().set(true);
        ext.getTruncateNotesAfterLines().set(8);
        ext.getDependencyColors().set(java.util.Map.of("HTTP", "#00ff00"));
        ext.getWriteCiSummary().set(true);
        ext.getMaxCiSummaryDiagrams().set(5);
        ext.getCiArtifactName().set("MyReports");
        ext.getCustomCss().set(".x{}");
        ext.getShowStepNumbers().set(true);

        Test test = (Test) project.getTasks().getByName("test");
        java.util.Map<String, Object> props = test.getSystemProperties();

        assertThat(props)
            .containsEntry("kronikol.diagram.arrowColors", "true")
            .containsEntry("kronikol.diagram.plantUmlTheme", "cerulean")
            .containsEntry("kronikol.report.dataFormats", "json,yaml")
            .containsEntry("kronikol.report.generateSchema", "true")
            .containsEntry("kronikol.diagram.truncateNotesAfterLines", "8")
            .containsEntry("kronikol.diagram.dependencyColors", "HTTP=#00ff00")
            .containsEntry("kronikol.ci.writeCiSummary", "true")
            .containsEntry("kronikol.ci.maxCiSummaryDiagrams", "5")
            .containsEntry("kronikol.ci.ciArtifactName", "MyReports")
            .containsEntry("kronikol.report.customCss", ".x{}")
            .containsEntry("kronikol.report.showStepNumbers", "true");
    }

    @org.junit.jupiter.api.Test
    void attachesAssertionAgentWhenOptedIn() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(KronikolPlugin.class);

        KronikolExtension ext = project.getExtensions().getByType(KronikolExtension.class);
        ext.getAttachAssertionAgent().set(true);

        Test test = (Test) project.getTasks().getByName("test");
        // a jvm-argument provider is wired onto the test task for the agent
        assertThat(test.getJvmArgumentProviders()).isNotEmpty();
        // the resolvable configuration exists and (opted in) declares the agent dependency
        var agentConfig = project.getConfigurations().findByName("kronikolAssertionAgent");
        assertThat(agentConfig).isNotNull();
        assertThat(agentConfig.getAllDependencies())
            .anySatisfy(d -> assertThat(d.getName()).isEqualTo("kronikol4j-assertj-agent"));
    }

    @org.junit.jupiter.api.Test
    void doesNotAttachAgentByDefault() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(KronikolPlugin.class);

        project.getTasks().getByName("test"); // realize
        var agentConfig = project.getConfigurations().findByName("kronikolAssertionAgent");
        assertThat(agentConfig).isNotNull();
        assertThat(agentConfig.getAllDependencies()).isEmpty(); // no agent dep unless opted in
    }

    @org.junit.jupiter.api.Test
    void doesNotForwardUnsetReportOptions() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(KronikolPlugin.class);

        // Nothing configured on the extension beyond the conventions.
        Test test = (Test) project.getTasks().getByName("test");
        java.util.Map<String, Object> props = test.getSystemProperties();

        assertThat(props)
            .doesNotContainKey("kronikol.diagram.arrowColors")
            .doesNotContainKey("kronikol.report.dataFormats")
            .doesNotContainKey("kronikol.ci.writeCiSummary")
            .doesNotContainKey("kronikol.report.customCss");
        assertThat(props).containsKey("kronikol.run.dir"); // the always-set fork marker remains
    }
}
