package io.kronikol.report.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.diagram.component.ArrowColorMode;
import io.kronikol.report.flow.InternalFlowDiagramStyle;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies the ComponentDiagramOptions defaults match the .NET record and the builder sets every field. */
class ComponentDiagramOptionsTest {

    @Test
    void defaultsMatchDotNet() {
        ComponentDiagramOptions o = ComponentDiagramOptions.defaults();
        assertThat(o.fileName()).isEqualTo("ComponentDiagram");
        assertThat(o.embedInTestRunReport()).isTrue();
        assertThat(o.title()).isEqualTo("Component Diagram");
        assertThat(o.plantUmlTheme()).isNull();
        assertThat(o.participantFilter()).isNull();
        assertThat(o.relationshipLabelFormatter()).isNull();
        assertThat(o.showRelationshipFlows()).isTrue();
        assertThat(o.relationshipFlowStyle()).isEqualTo(InternalFlowDiagramStyle.ACTIVITY_DIAGRAM);
        assertThat(o.showSystemFlameChart()).isTrue();
        assertThat(o.lowCoverageThreshold()).isEqualTo(3);
        assertThat(o.arrowColorMode()).isEqualTo(ArrowColorMode.DEPENDENCY_TYPE);
        assertThat(o.dependencyColors()).isNull();
        assertThat(o.maxFlameChartTests()).isEqualTo(50);
    }

    @Test
    void builderSetsValues() {
        ComponentDiagramOptions o = ComponentDiagramOptions.builder()
            .fileName("arch").embedInTestRunReport(false).title("Architecture")
            .plantUmlTheme("aws-orange").participantFilter(name -> name.startsWith("svc"))
            .relationshipLabelFormatter(rel -> rel.caller() + "->" + rel.service())
            .showRelationshipFlows(false).relationshipFlowStyle(InternalFlowDiagramStyle.SEQUENCE_DIAGRAM)
            .showSystemFlameChart(false).lowCoverageThreshold(10)
            .arrowColorMode(ArrowColorMode.PERFORMANCE)
            .dependencyColors(Map.of("HTTP", "#00ff00")).maxFlameChartTests(25)
            .build();

        assertThat(o.fileName()).isEqualTo("arch");
        assertThat(o.embedInTestRunReport()).isFalse();
        assertThat(o.title()).isEqualTo("Architecture");
        assertThat(o.plantUmlTheme()).isEqualTo("aws-orange");
        assertThat(o.participantFilter().test("svc-a")).isTrue();
        assertThat(o.showRelationshipFlows()).isFalse();
        assertThat(o.relationshipFlowStyle()).isEqualTo(InternalFlowDiagramStyle.SEQUENCE_DIAGRAM);
        assertThat(o.showSystemFlameChart()).isFalse();
        assertThat(o.lowCoverageThreshold()).isEqualTo(10);
        assertThat(o.arrowColorMode()).isEqualTo(ArrowColorMode.PERFORMANCE);
        assertThat(o.dependencyColors()).containsEntry("HTTP", "#00ff00");
        assertThat(o.maxFlameChartTests()).isEqualTo(25);
    }
}
