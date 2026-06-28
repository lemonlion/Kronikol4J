package io.kronikol.report.flow;

import java.util.List;
import java.util.Map;

/**
 * Consolidated configuration surface for the internal-flow feature — the Java home for the .NET
 * {@code ReportConfigurationOptions.InternalFlow*} option group. It carries every internal-flow option that
 * actually changes rendered output, with the .NET defaults, and builds the two render inputs the report
 * pipeline consumes: {@link #toPopupInput(Map, int)} → {@link InternalFlowPopupInput} (the interactive
 * per-step popups) and {@link #toWholeTestFlowInput(Map, Map)} → {@link WholeTestFlowInput} (the whole-test
 * activity diagram + flame chart). A span-collecting caller sets this once instead of threading nine loose
 * arguments through each render call.
 *
 * <p><strong>Deliberately not ported (verified .NET dead-config):</strong> the .NET options
 * {@code InternalFlowDisplay}, {@code InternalFlowTrigger}, {@code InternalFlowContentStrategy},
 * {@code InternalFlowFragmentsFolderName}, and {@code InternalFlowPopupCustomStyleSheet} have <em>no
 * consumer</em> in the .NET source — neither the report-generation pipeline nor the client-side popup JS
 * reads them (the emitted {@code window.__iflowConfig} carries only {@code hasDataBehavior}). Porting them
 * as Java options would be configuration that gates nothing (a stub), so they are intentionally omitted; if
 * .NET later wires them, add them here alongside the consumer.
 */
public final class InternalFlowOptions {

    private static final InternalFlowOptions DEFAULTS = builder().build();

    private final boolean internalFlowTracking;
    private final InternalFlowDiagramStyle diagramStyle;
    private final InternalFlowSpanGranularity spanGranularity;
    private final InternalFlowNoDataBehavior noDataBehavior;
    private final InternalFlowHasDataBehavior hasDataBehavior;
    private final boolean showFlameChart;
    private final InternalFlowFlameChartPosition flameChartPosition;
    private final String[] activitySources;
    private final WholeTestFlowVisualization wholeTestFlowVisualization;

    private InternalFlowOptions(Builder b) {
        this.internalFlowTracking = b.internalFlowTracking;
        this.diagramStyle = b.diagramStyle;
        this.spanGranularity = b.spanGranularity;
        this.noDataBehavior = b.noDataBehavior;
        this.hasDataBehavior = b.hasDataBehavior;
        this.showFlameChart = b.showFlameChart;
        this.flameChartPosition = b.flameChartPosition;
        this.activitySources = b.activitySources == null ? null : b.activitySources.clone();
        this.wholeTestFlowVisualization = b.wholeTestFlowVisualization;
    }

    public boolean internalFlowTracking() { return internalFlowTracking; }
    public InternalFlowDiagramStyle diagramStyle() { return diagramStyle; }
    public InternalFlowSpanGranularity spanGranularity() { return spanGranularity; }
    public InternalFlowNoDataBehavior noDataBehavior() { return noDataBehavior; }
    public InternalFlowHasDataBehavior hasDataBehavior() { return hasDataBehavior; }
    public boolean showFlameChart() { return showFlameChart; }
    public InternalFlowFlameChartPosition flameChartPosition() { return flameChartPosition; }

    /** The explicit OTel source allowlist, or {@code null} to include all sources (defensive copy). */
    public String[] activitySources() { return activitySources == null ? null : activitySources.clone(); }

    public WholeTestFlowVisualization wholeTestFlowVisualization() { return wholeTestFlowVisualization; }

    /** The .NET defaults (a cached singleton — value-equality is impractical for the array field). */
    public static InternalFlowOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Builds the interactive popup input from these options plus the per-render data
     * ({@code perDiagramSegments} keyed {@code iflow-{requestId}} and the total spans seen in the store).
     */
    public InternalFlowPopupInput toPopupInput(Map<String, InternalFlowSegment> perDiagramSegments,
                                               int totalSpansInStore) {
        return new InternalFlowPopupInput(perDiagramSegments, diagramStyle, showFlameChart, flameChartPosition,
            noDataBehavior, spanGranularity, activitySources(), totalSpansInStore, hasDataBehavior,
            internalFlowTracking);
    }

    /**
     * Builds the whole-test-flow input from these options plus the per-render data ({@code segments} keyed
     * {@code iflow-test-{testId}} and the per-test flame {@code boundaryMarkers}).
     */
    public WholeTestFlowInput toWholeTestFlowInput(Map<String, InternalFlowSegment> segments,
            Map<String, List<InternalFlowRenderer.BoundaryMarker>> boundaryMarkers) {
        return new WholeTestFlowInput(segments, wholeTestFlowVisualization, boundaryMarkers,
            internalFlowTracking);
    }

    /** Returns a copy with {@link #showFlameChart()} overridden. */
    public InternalFlowOptions withShowFlameChart(boolean value) {
        return toBuilder().showFlameChart(value).build();
    }

    /** Returns a copy with {@link #wholeTestFlowVisualization()} overridden. */
    public InternalFlowOptions withWholeTestFlowVisualization(WholeTestFlowVisualization value) {
        return toBuilder().wholeTestFlowVisualization(value).build();
    }

    /** Returns a copy with {@link #internalFlowTracking()} overridden. */
    public InternalFlowOptions withInternalFlowTracking(boolean value) {
        return toBuilder().internalFlowTracking(value).build();
    }

    private Builder toBuilder() {
        return new Builder()
            .internalFlowTracking(internalFlowTracking)
            .diagramStyle(diagramStyle)
            .spanGranularity(spanGranularity)
            .noDataBehavior(noDataBehavior)
            .hasDataBehavior(hasDataBehavior)
            .showFlameChart(showFlameChart)
            .flameChartPosition(flameChartPosition)
            .activitySources(activitySources)
            .wholeTestFlowVisualization(wholeTestFlowVisualization);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder; all defaults mirror the .NET {@code ReportConfigurationOptions} internal-flow defaults. */
    public static final class Builder {
        private boolean internalFlowTracking = true;
        private InternalFlowDiagramStyle diagramStyle = InternalFlowDiagramStyle.ACTIVITY_DIAGRAM;
        private InternalFlowSpanGranularity spanGranularity = InternalFlowSpanGranularity.AUTO_INSTRUMENTATION;
        private InternalFlowNoDataBehavior noDataBehavior = InternalFlowNoDataBehavior.HIDE_LINK;
        private InternalFlowHasDataBehavior hasDataBehavior = InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER;
        private boolean showFlameChart = true;
        private InternalFlowFlameChartPosition flameChartPosition =
            InternalFlowFlameChartPosition.BEHIND_WITH_TOGGLE;
        private String[] activitySources;
        private WholeTestFlowVisualization wholeTestFlowVisualization = WholeTestFlowVisualization.BOTH;

        public Builder internalFlowTracking(boolean v) { this.internalFlowTracking = v; return this; }

        public Builder diagramStyle(InternalFlowDiagramStyle v) {
            this.diagramStyle = v == null ? InternalFlowDiagramStyle.ACTIVITY_DIAGRAM : v;
            return this;
        }

        public Builder spanGranularity(InternalFlowSpanGranularity v) {
            this.spanGranularity = v == null ? InternalFlowSpanGranularity.AUTO_INSTRUMENTATION : v;
            return this;
        }

        public Builder noDataBehavior(InternalFlowNoDataBehavior v) {
            this.noDataBehavior = v == null ? InternalFlowNoDataBehavior.HIDE_LINK : v;
            return this;
        }

        public Builder hasDataBehavior(InternalFlowHasDataBehavior v) {
            this.hasDataBehavior = v == null ? InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER : v;
            return this;
        }

        public Builder showFlameChart(boolean v) { this.showFlameChart = v; return this; }

        public Builder flameChartPosition(InternalFlowFlameChartPosition v) {
            this.flameChartPosition = v == null ? InternalFlowFlameChartPosition.BEHIND_WITH_TOGGLE : v;
            return this;
        }

        public Builder activitySources(String[] v) {
            this.activitySources = v == null ? null : v.clone();
            return this;
        }

        public Builder wholeTestFlowVisualization(WholeTestFlowVisualization v) {
            this.wholeTestFlowVisualization = v == null ? WholeTestFlowVisualization.BOTH : v;
            return this;
        }

        public InternalFlowOptions build() {
            return new InternalFlowOptions(this);
        }
    }
}
