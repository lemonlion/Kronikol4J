package io.kronikol.report.flow;

/**
 * The resolved whole-test-flow content for one test (.NET {@code ResolveWholeTestFlowContent}'s tuple):
 * the activity-diagram HTML, the flame-chart HTML, and the span count (for the outlier warning).
 * Either HTML may be empty when the {@link WholeTestFlowVisualization} excludes that view.
 */
public record WholeTestFlowContent(String activityHtml, String flameHtml, int spanCount) {

    public boolean hasActivity() {
        return activityHtml != null && !activityHtml.isEmpty();
    }

    public boolean hasFlame() {
        return flameHtml != null && !flameHtml.isEmpty();
    }
}
