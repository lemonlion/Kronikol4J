package io.kronikol.report.flow;

/**
 * Which internal-flow visualizations are generated alongside the sequence diagrams (.NET
 * {@code WholeTestFlowVisualization}).
 */
public enum WholeTestFlowVisualization {
    /** No internal-flow visualization. */
    NONE,
    /** A flame chart of span durations. */
    FLAME_CHART,
    /** A PlantUML activity diagram from the captured spans. */
    ACTIVITY_DIAGRAM,
    /** Both a flame chart and an activity diagram. */
    BOTH
}
