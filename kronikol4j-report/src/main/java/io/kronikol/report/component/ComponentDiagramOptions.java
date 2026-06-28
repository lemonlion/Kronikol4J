package io.kronikol.report.component;

import io.kronikol.diagram.component.ArrowColorMode;
import io.kronikol.diagram.component.ComponentRelationship;
import io.kronikol.report.flow.InternalFlowDiagramStyle;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Options for C4-style component-diagram generation. Java port of the .NET {@code ComponentDiagramOptions}.
 * Lives in the report module (not diagram) because it references {@link InternalFlowDiagramStyle} (report) and
 * is consumed by the report orchestration that embeds the component diagram; it composes diagram-module types
 * ({@link ArrowColorMode}, {@link ComponentRelationship}) which report already depends on. Built via
 * {@link #builder()}.
 */
public final class ComponentDiagramOptions {

    private static final ComponentDiagramOptions DEFAULTS = builder().build();

    private final String fileName;
    private final boolean embedInTestRunReport;
    private final String title;
    private final String plantUmlTheme;
    private final Predicate<String> participantFilter;
    private final Function<ComponentRelationship, String> relationshipLabelFormatter;
    private final boolean showRelationshipFlows;
    private final InternalFlowDiagramStyle relationshipFlowStyle;
    private final boolean showSystemFlameChart;
    private final int lowCoverageThreshold;
    private final ArrowColorMode arrowColorMode;
    private final Map<String, String> dependencyColors;
    private final int maxFlameChartTests;

    private ComponentDiagramOptions(Builder b) {
        this.fileName = b.fileName;
        this.embedInTestRunReport = b.embedInTestRunReport;
        this.title = b.title;
        this.plantUmlTheme = b.plantUmlTheme;
        this.participantFilter = b.participantFilter;
        this.relationshipLabelFormatter = b.relationshipLabelFormatter;
        this.showRelationshipFlows = b.showRelationshipFlows;
        this.relationshipFlowStyle = b.relationshipFlowStyle;
        this.showSystemFlameChart = b.showSystemFlameChart;
        this.lowCoverageThreshold = b.lowCoverageThreshold;
        this.arrowColorMode = b.arrowColorMode;
        this.dependencyColors = b.dependencyColors;
        this.maxFlameChartTests = b.maxFlameChartTests;
    }

    public String fileName() { return fileName; }
    public boolean embedInTestRunReport() { return embedInTestRunReport; }
    public String title() { return title; }
    public String plantUmlTheme() { return plantUmlTheme; }
    public Predicate<String> participantFilter() { return participantFilter; }
    public Function<ComponentRelationship, String> relationshipLabelFormatter() { return relationshipLabelFormatter; }
    public boolean showRelationshipFlows() { return showRelationshipFlows; }
    public InternalFlowDiagramStyle relationshipFlowStyle() { return relationshipFlowStyle; }
    public boolean showSystemFlameChart() { return showSystemFlameChart; }
    public int lowCoverageThreshold() { return lowCoverageThreshold; }
    public ArrowColorMode arrowColorMode() { return arrowColorMode; }
    public Map<String, String> dependencyColors() { return dependencyColors; }
    public int maxFlameChartTests() { return maxFlameChartTests; }

    /** The default options (matching the .NET defaults) — a cached singleton so a record holding the
     *  defaults compares equal by reference (the function fields make value-equality impractical). */
    public static ComponentDiagramOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ComponentDiagramOptions}; all defaults mirror the .NET record. */
    public static final class Builder {
        private String fileName = "ComponentDiagram";
        private boolean embedInTestRunReport = true;
        private String title = "Component Diagram";
        private String plantUmlTheme;
        private Predicate<String> participantFilter;
        private Function<ComponentRelationship, String> relationshipLabelFormatter;
        private boolean showRelationshipFlows = true;
        private InternalFlowDiagramStyle relationshipFlowStyle = InternalFlowDiagramStyle.ACTIVITY_DIAGRAM;
        private boolean showSystemFlameChart = true;
        private int lowCoverageThreshold = 3;
        private ArrowColorMode arrowColorMode = ArrowColorMode.DEPENDENCY_TYPE;
        private Map<String, String> dependencyColors;
        private int maxFlameChartTests = 50;

        public Builder fileName(String v) { this.fileName = v; return this; }
        public Builder embedInTestRunReport(boolean v) { this.embedInTestRunReport = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder plantUmlTheme(String v) { this.plantUmlTheme = v; return this; }
        public Builder participantFilter(Predicate<String> v) { this.participantFilter = v; return this; }
        public Builder relationshipLabelFormatter(Function<ComponentRelationship, String> v) {
            this.relationshipLabelFormatter = v;
            return this;
        }
        public Builder showRelationshipFlows(boolean v) { this.showRelationshipFlows = v; return this; }
        public Builder relationshipFlowStyle(InternalFlowDiagramStyle v) {
            this.relationshipFlowStyle = v == null ? InternalFlowDiagramStyle.ACTIVITY_DIAGRAM : v;
            return this;
        }
        public Builder showSystemFlameChart(boolean v) { this.showSystemFlameChart = v; return this; }
        public Builder lowCoverageThreshold(int v) { this.lowCoverageThreshold = v; return this; }
        public Builder arrowColorMode(ArrowColorMode v) {
            this.arrowColorMode = v == null ? ArrowColorMode.DEPENDENCY_TYPE : v;
            return this;
        }
        public Builder dependencyColors(Map<String, String> v) { this.dependencyColors = v; return this; }
        public Builder maxFlameChartTests(int v) { this.maxFlameChartTests = v; return this; }

        public ComponentDiagramOptions build() {
            return new ComponentDiagramOptions(this);
        }
    }
}
