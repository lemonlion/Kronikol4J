package io.kronikol.gradle;

import io.kronikol.report.ReportOptions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
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
        extension.getAttachAssertionAgent().convention(false);

        Provider<Directory> fragmentsDir = project.getLayout().getBuildDirectory().dir("kronikol-fragments");

        // Build-time weaving auto-wiring: a resolvable configuration holding the assertion agent, populated
        // (only when opted in) at resolution time so the user can set the flag/coordinates in kronikol { }.
        Configuration agentConfig = project.getConfigurations().create("kronikolAssertionAgent", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setVisible(false);
            c.setDescription("The Kronikol4J assertion-tracking agent attached to test JVMs.");
        });
        agentConfig.getDependencies().addAllLater(project.provider(() ->
            Boolean.TRUE.equals(extension.getAttachAssertionAgent().getOrElse(false))
                ? List.of(project.getDependencies().create(agentCoordinates(extension)))
                : List.of()));

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
            forwardReportOptions(extension, test);
            // Lazily attach the assertion agent (resolved at execution; empty args when not opted in).
            test.getJvmArgumentProviders().add(() -> AssertionAgentArgs.compute(
                Boolean.TRUE.equals(extension.getAttachAssertionAgent().getOrElse(false)),
                agentConfig.getFiles().isEmpty() ? null : agentConfig.getSingleFile()));
        });
    }

    /** The agent coordinates: the user override, else this plugin's own version. */
    private static String agentCoordinates(KronikolExtension extension) {
        String override = extension.getAssertionAgentCoordinates().getOrNull();
        if (override != null && !override.isBlank()) {
            return override;
        }
        String version = Optional.ofNullable(KronikolPlugin.class.getPackage().getImplementationVersion())
            .orElse("0.1.25-SNAPSHOT");
        return "io.github.lemonlion:kronikol4j-assertj-agent:" + version;
    }

    /**
     * Forwards each {@link KronikolExtension} value the user actually set to the matching
     * {@code -Dkronikol.*} system property on {@code test}, so the forked JVM's
     * {@link ReportOptions#fromSystemProperties()} reads it. Unset properties are left untouched (the report
     * keeps its default). Read lazily at task realization — after the {@code kronikol { }} block evaluates.
     */
    private static void forwardReportOptions(KronikolExtension ext, Test test) {
        // diagram styling
        forwardBool(test, ReportOptions.ARROW_COLORS_PROPERTY, ext.getArrowColors());
        forwardBool(test, ReportOptions.PARTICIPANT_COLORS_PROPERTY, ext.getParticipantColors());
        forwardString(test, ReportOptions.PLANTUML_THEME_PROPERTY, ext.getPlantUmlTheme());
        forwardBool(test, ReportOptions.SEPARATE_SETUP_PROPERTY, ext.getSeparateSetup());
        forwardBool(test, ReportOptions.HIGHLIGHT_SETUP_PROPERTY, ext.getHighlightSetup());
        forwardString(test, ReportOptions.SETUP_HIGHLIGHT_COLOR_PROPERTY, ext.getSetupHighlightColor());
        forwardList(test, ReportOptions.EXCLUDED_HEADERS_PROPERTY, ext.getExcludedHeaders());
        forwardBool(test, ReportOptions.EXCLUDE_ALL_HEADERS_PROPERTY, ext.getExcludeAllHeaders());
        forwardList(test, ReportOptions.FOCUS_EMPHASIS_PROPERTY, ext.getFocusEmphasis());
        forwardList(test, ReportOptions.FOCUS_DE_EMPHASIS_PROPERTY, ext.getFocusDeEmphasis());
        forwardString(test, ReportOptions.GRAPHQL_BODY_FORMAT_PROPERTY, ext.getGraphQlBodyFormat());
        forwardBool(test, ReportOptions.INTERNAL_FLOW_TRACKING_PROPERTY, ext.getInternalFlowTracking());
        forwardInt(test, ReportOptions.TRUNCATE_NOTES_PROPERTY, ext.getTruncateNotesAfterLines());
        forwardMap(test, ReportOptions.DEPENDENCY_COLORS_PROPERTY, ext.getDependencyColors());
        forwardMap(test, ReportOptions.SERVICE_TYPE_OVERRIDES_PROPERTY, ext.getServiceTypeOverrides());
        // report data
        forwardList(test, ReportOptions.DATA_FORMATS_PROPERTY, ext.getDataFormats());
        forwardBool(test, ReportOptions.GENERATE_SCHEMA_PROPERTY, ext.getGenerateSchema());
        // HTML customization
        forwardString(test, ReportOptions.CUSTOM_CSS_PROPERTY, ext.getCustomCss());
        forwardString(test, ReportOptions.CUSTOM_STYLESHEET_PROPERTY, ext.getCustomStyleSheet());
        forwardString(test, ReportOptions.CUSTOM_FAVICON_PROPERTY, ext.getCustomFaviconBase64());
        forwardString(test, ReportOptions.CUSTOM_LOGO_PROPERTY, ext.getCustomLogoHtml());
        forwardBool(test, ReportOptions.SHOW_STEP_NUMBERS_PROPERTY, ext.getShowStepNumbers());
        forwardBool(test, ReportOptions.BLANK_ON_FAILED_PROPERTY, ext.getGenerateBlankOnFailedTests());
        forwardBool(test, ReportOptions.DIAGNOSTIC_MODE_PROPERTY, ext.getDiagnosticMode());
        forwardString(test, ReportOptions.REPORT_TITLE_PROPERTY, ext.getTestRunReportTitle());
        forwardString(test, ReportOptions.HTML_FILE_NAME_PROPERTY, ext.getHtmlReportFileName());
        forwardBool(test, ReportOptions.GENERATE_COMPONENT_DIAGRAM_PROPERTY, ext.getGenerateComponentDiagram());
        forwardBool(test, ReportOptions.GENERATE_MERGEABLE_DATA_PROPERTY, ext.getGenerateMergeableData());
        forwardBool(test, ReportOptions.GENERATE_TEST_RUN_REPORT_DATA_PROPERTY,
            ext.getGenerateTestRunReportData());
        // specifications report
        forwardBool(test, io.kronikol.report.spec.SpecificationsOptions.GENERATE_REPORT_PROPERTY,
            ext.getGenerateSpecificationsReport());
        forwardBool(test, io.kronikol.report.spec.SpecificationsOptions.GENERATE_DATA_PROPERTY,
            ext.getGenerateSpecificationsData());
        forwardString(test, io.kronikol.report.spec.SpecificationsOptions.TITLE_PROPERTY,
            ext.getSpecificationsTitle());
        forwardString(test, io.kronikol.report.spec.SpecificationsOptions.DATA_FORMAT_PROPERTY,
            ext.getSpecificationsDataFormat());
        forwardInt(test, ReportOptions.EXPECTED_TEST_COUNT_PROPERTY, ext.getExpectedTestCount());
        // CI summary / artifacts
        forwardBool(test, ReportOptions.WRITE_CI_SUMMARY_PROPERTY, ext.getWriteCiSummary());
        forwardInt(test, ReportOptions.MAX_CI_SUMMARY_DIAGRAMS_PROPERTY, ext.getMaxCiSummaryDiagrams());
        forwardBool(test, ReportOptions.PUBLISH_CI_ARTIFACTS_PROPERTY, ext.getPublishCiArtifacts());
        forwardString(test, ReportOptions.CI_ARTIFACT_NAME_PROPERTY, ext.getCiArtifactName());
        forwardInt(test, ReportOptions.CI_ARTIFACT_RETENTION_DAYS_PROPERTY, ext.getCiArtifactRetentionDays());
    }

    private static void forwardBool(Test test, String key, Property<Boolean> value) {
        if (value.isPresent()) {
            test.systemProperty(key, value.get().toString());
        }
    }

    private static void forwardInt(Test test, String key, Property<Integer> value) {
        if (value.isPresent()) {
            test.systemProperty(key, value.get().toString());
        }
    }

    private static void forwardString(Test test, String key, Property<String> value) {
        String v = value.getOrNull();
        if (v != null && !v.isBlank()) {
            test.systemProperty(key, v);
        }
    }

    private static void forwardList(Test test, String key, ListProperty<String> value) {
        List<String> list = value.getOrNull();
        if (list != null && !list.isEmpty()) {
            test.systemProperty(key, String.join(",", list));
        }
    }

    private static void forwardMap(Test test, String key, MapProperty<String, String> value) {
        Map<String, String> map = value.getOrNull();
        if (map != null && !map.isEmpty()) {
            test.systemProperty(key, map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",")));
        }
    }
}
