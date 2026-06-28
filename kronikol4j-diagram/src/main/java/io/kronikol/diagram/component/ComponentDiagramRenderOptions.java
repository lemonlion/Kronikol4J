package io.kronikol.diagram.component;

import java.util.Map;
import java.util.function.Function;

/**
 * The render-time subset of the .NET {@code ComponentDiagramOptions} that
 * {@link ComponentDiagramGenerator#generatePlantUml} actually consumes: the diagram {@code title}, an optional
 * PlantUML {@code !theme}, a custom relationship-label formatter, the {@link ArrowColorMode}, and per-category
 * arrow-colour overrides.
 *
 * <p>It lives in the <strong>diagram</strong> module (not report) so the generator can take it without the
 * report→diagram dependency cycle the user-facing {@code io.kronikol.report.component.ComponentDiagramOptions}
 * would introduce. The report orchestration maps the render-relevant fields of its full options surface onto
 * this. Stats-driven fields (relationship flows, hotspot colouring, low-coverage, flame chart) are not here —
 * they belong to the not-yet-ported {@code RelationshipStats} machinery and would be additional parameters.
 */
public final class ComponentDiagramRenderOptions {

    private static final ComponentDiagramRenderOptions DEFAULTS = builder().build();

    private final String title;
    private final String plantUmlTheme;
    private final Function<ComponentRelationship, String> relationshipLabelFormatter;
    private final ArrowColorMode arrowColorMode;
    private final Map<String, String> dependencyColors;

    private ComponentDiagramRenderOptions(Builder b) {
        this.title = b.title;
        this.plantUmlTheme = b.plantUmlTheme;
        this.relationshipLabelFormatter = b.relationshipLabelFormatter;
        this.arrowColorMode = b.arrowColorMode;
        this.dependencyColors = b.dependencyColors;
    }

    public String title() { return title; }
    public String plantUmlTheme() { return plantUmlTheme; }
    public Function<ComponentRelationship, String> relationshipLabelFormatter() { return relationshipLabelFormatter; }
    public ArrowColorMode arrowColorMode() { return arrowColorMode; }
    public Map<String, String> dependencyColors() { return dependencyColors; }

    /** The defaults matching .NET: title {@code "Component Diagram"}, no theme, {@link ArrowColorMode#DEPENDENCY_TYPE}. */
    public static ComponentDiagramRenderOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder; all defaults mirror the .NET {@code ComponentDiagramOptions} render-relevant defaults. */
    public static final class Builder {
        private String title = "Component Diagram";
        private String plantUmlTheme;
        private Function<ComponentRelationship, String> relationshipLabelFormatter;
        private ArrowColorMode arrowColorMode = ArrowColorMode.DEPENDENCY_TYPE;
        private Map<String, String> dependencyColors;

        public Builder title(String v) {
            this.title = v == null ? "Component Diagram" : v;
            return this;
        }

        public Builder plantUmlTheme(String v) { this.plantUmlTheme = v; return this; }

        public Builder relationshipLabelFormatter(Function<ComponentRelationship, String> v) {
            this.relationshipLabelFormatter = v;
            return this;
        }

        public Builder arrowColorMode(ArrowColorMode v) {
            this.arrowColorMode = v == null ? ArrowColorMode.DEPENDENCY_TYPE : v;
            return this;
        }

        public Builder dependencyColors(Map<String, String> v) { this.dependencyColors = v; return this; }

        public ComponentDiagramRenderOptions build() {
            return new ComponentDiagramRenderOptions(this);
        }
    }
}
