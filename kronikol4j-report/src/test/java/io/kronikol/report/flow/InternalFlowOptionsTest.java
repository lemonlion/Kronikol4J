package io.kronikol.report.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link InternalFlowOptions} config surface — the consolidated, .NET-defaulted home for the
 * internal-flow sub-options (the .NET {@code ReportConfigurationOptions.InternalFlow*} group) — and that it
 * genuinely drives the two render inputs ({@link InternalFlowPopupInput} / {@link WholeTestFlowInput}).
 */
class InternalFlowOptionsTest {

    @Test
    void defaultsMatchTheDotNetReportConfigurationOptions() {
        InternalFlowOptions o = InternalFlowOptions.defaults();

        assertThat(o.internalFlowTracking()).isTrue();
        assertThat(o.diagramStyle()).isEqualTo(InternalFlowDiagramStyle.ACTIVITY_DIAGRAM);
        assertThat(o.spanGranularity()).isEqualTo(InternalFlowSpanGranularity.AUTO_INSTRUMENTATION);
        assertThat(o.noDataBehavior()).isEqualTo(InternalFlowNoDataBehavior.HIDE_LINK);
        assertThat(o.hasDataBehavior()).isEqualTo(InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER);
        assertThat(o.showFlameChart()).isTrue();
        assertThat(o.flameChartPosition()).isEqualTo(InternalFlowFlameChartPosition.BEHIND_WITH_TOGGLE);
        assertThat(o.activitySources()).isNull();
        assertThat(o.wholeTestFlowVisualization()).isEqualTo(WholeTestFlowVisualization.BOTH);
    }

    @Test
    void buildsAPopupInputCarryingEveryConsumedOption() {
        InternalFlowOptions o = InternalFlowOptions.builder()
            .diagramStyle(InternalFlowDiagramStyle.SEQUENCE_DIAGRAM)
            .spanGranularity(InternalFlowSpanGranularity.FULL)
            .noDataBehavior(InternalFlowNoDataBehavior.SHOW_MESSAGE)
            .hasDataBehavior(InternalFlowHasDataBehavior.SHOW_LINK)
            .showFlameChart(false)
            .flameChartPosition(InternalFlowFlameChartPosition.UNDERNEATH)
            .activitySources(new String[] {"MySource"})
            .build();

        InternalFlowPopupInput popup = o.toPopupInput(Map.of(), 7);

        assertThat(popup.diagramStyle()).isEqualTo(InternalFlowDiagramStyle.SEQUENCE_DIAGRAM);
        assertThat(popup.granularity()).isEqualTo(InternalFlowSpanGranularity.FULL);
        assertThat(popup.noDataBehavior()).isEqualTo(InternalFlowNoDataBehavior.SHOW_MESSAGE);
        assertThat(popup.hasDataBehavior()).isEqualTo(InternalFlowHasDataBehavior.SHOW_LINK);
        assertThat(popup.showFlameChart()).isFalse();
        assertThat(popup.flameChartPosition()).isEqualTo(InternalFlowFlameChartPosition.UNDERNEATH);
        assertThat(popup.configuredActivitySources()).containsExactly("MySource");
        assertThat(popup.totalSpansInStore()).isEqualTo(7);
        assertThat(popup.internalFlowTracking()).isTrue();
    }

    @Test
    void disabledTrackingProducesTheInertPopupInput() {
        InternalFlowOptions o = InternalFlowOptions.builder().internalFlowTracking(false).build();

        InternalFlowPopupInput popup = o.toPopupInput(Map.of(), 0);

        assertThat(popup.internalFlowTracking()).isFalse();
        assertThat(popup.dataScript()).isEmpty(); // tracking off → no scripts emitted
    }

    @Test
    void buildsAWholeTestFlowInputWithTheChosenVisualization() {
        InternalFlowOptions o = InternalFlowOptions.builder()
            .wholeTestFlowVisualization(WholeTestFlowVisualization.FLAME_CHART).build();

        WholeTestFlowInput wtf = o.toWholeTestFlowInput(Map.of(), Map.of());

        assertThat(wtf.visualization()).isEqualTo(WholeTestFlowVisualization.FLAME_CHART);
        assertThat(wtf.internalFlowTracking()).isTrue();
    }

    @Test
    void activitySourcesAreDefensivelyCopied() {
        String[] sources = {"A"};
        InternalFlowOptions o = InternalFlowOptions.builder().activitySources(sources).build();
        sources[0] = "MUTATED";

        assertThat(o.activitySources()).containsExactly("A"); // snapshot taken at build time
    }

    @Test
    void withersOnAnOptionsInstanceAreIndependent() {
        InternalFlowOptions base = InternalFlowOptions.defaults();
        InternalFlowOptions tweaked = base.withShowFlameChart(false);

        assertThat(base.showFlameChart()).isTrue();
        assertThat(tweaked.showFlameChart()).isFalse();
        assertThat(tweaked.diagramStyle()).isEqualTo(base.diagramStyle()); // others preserved
    }

    @Test
    void boundaryMarkersFlowIntoTheWholeTestFlowInput() {
        var marker = new InternalFlowRenderer.BoundaryMarker("Action", java.time.Instant.EPOCH);
        InternalFlowOptions o = InternalFlowOptions.defaults();

        WholeTestFlowInput wtf = o.toWholeTestFlowInput(Map.of(), Map.of("t1", List.of(marker)));

        assertThat(wtf.boundaryMarkers()).containsKey("t1");
    }
}
