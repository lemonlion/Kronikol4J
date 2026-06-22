package io.kronikol.report.merge;

import io.kronikol.diagram.component.ComponentDiagramGenerator;
import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.flow.InternalFlowDiagramStyle;
import io.kronikol.report.flow.InternalFlowFlameChartPosition;
import io.kronikol.report.flow.InternalFlowHasDataBehavior;
import io.kronikol.report.flow.InternalFlowNoDataBehavior;
import io.kronikol.report.flow.InternalFlowPopupInput;
import io.kronikol.report.flow.InternalFlowSpanGranularity;
import io.kronikol.report.flow.WholeTestFlowInput;
import io.kronikol.report.model.HtmlCustomization;
import java.time.Instant;

/**
 * Renders a (typically merged) {@link ReportFragment} to the interactive HTML report — the .NET
 * {@code MergeableReportRenderer} analog. Reproduces the same report a single combined run would have
 * produced: the full per-scenario step/parameter/attachment detail, each scenario's pre-computed
 * sequence/activity diagram, the run-level component diagram (from the aggregated relationships), the
 * interactive internal-flow popup, the whole-test-flow views, the CI-metadata banner, and the test-run
 * summary.
 */
public final class MergeableReportRenderer {

    private MergeableReportRenderer() {
    }

    public static String renderHtml(ReportFragment fragment) {
        String title = fragment.title() == null ? "Test Run Report" : fragment.title();

        // Run-level component diagram from the merged relationships (browser non-C4 dialect).
        String componentDiagram = fragment.componentRelationships().isEmpty()
            ? null : ComponentDiagramGenerator.generatePlantUml(fragment.componentRelationships());

        // Interactive popup from the per-diagram segments (internalFlowTracking on emits the head scripts).
        InternalFlowPopupInput popup = fragment.internalFlowSegments().isEmpty()
            ? InternalFlowPopupInput.NONE
            : new InternalFlowPopupInput(fragment.internalFlowSegments(),
                InternalFlowDiagramStyle.ACTIVITY_DIAGRAM, false,
                InternalFlowFlameChartPosition.BEHIND_WITH_TOGGLE, InternalFlowNoDataBehavior.HIDE_LINK,
                InternalFlowSpanGranularity.AUTO_INSTRUMENTATION, null, 0,
                InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER, true);

        // Whole-test-flow rendered from the precomputed fragments (no re-resolution from spans).
        WholeTestFlowInput wholeTestFlow = fragment.wholeTestFlow().isEmpty()
            ? WholeTestFlowInput.NONE : WholeTestFlowInput.precomputed(fragment.wholeTestFlow());

        HtmlCustomization custom = fragment.ciMetadata() == null
            ? HtmlCustomization.NONE
            : new HtmlCustomization(fragment.ciMetadata(), null, null, null, false, false);

        // The combined TestRunReport always includes the test-run-data summary (mirrors .NET).
        return HtmlReportGenerator.renderHtml(fragment.features(), fragment.diagramByTestId(),
            componentDiagram, title, true, parseInstant(fragment.startTime()),
            parseInstant(fragment.endTime()), custom, wholeTestFlow, popup);
    }

    private static Instant parseInstant(String iso) {
        return iso == null || iso.isEmpty() ? Instant.EPOCH : Instant.parse(iso);
    }
}
