package io.kronikol.report.diagnostics;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Analyses captured logs and features to produce diagnostic warnings (a port of the .NET
 * {@code ReportDiagnostics.Analyse}). Covers the deterministic warnings driven by logs + features +
 * span count: the log-entry/test summary, the no-test-contexts warning, unpaired requests, orphaned
 * test ids, and the span-store note.
 *
 * <p>The .NET also appends warnings derived from global runtime registries — discovered activity
 * sources, unused tracking components, and unresolved assertion arguments ({@code Track.DiagnosticLog})
 * — which read mutable .NET-pipeline state with no portable equivalent (the same environment boundary
 * as finished-span capture). Those are conditional on non-empty state, so they are absent here. The
 * span count is supplied by the caller (default 0).
 */
public final class ReportDiagnostics {

    private ReportDiagnostics() {
    }

    public static List<String> analyse(List<RequestResponseLog> logs, List<Feature> features) {
        return analyse(logs, features, 0);
    }

    public static List<String> analyse(List<RequestResponseLog> logs, List<Feature> features, int spanCount) {
        if (logs.isEmpty() && features.isEmpty()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        Set<String> distinctTestIds = new LinkedHashSet<>();
        for (RequestResponseLog l : logs) {
            distinctTestIds.add(l.testId());
        }
        warnings.add("Report diagnostics: " + logs.size() + " log entries across "
            + distinctTestIds.size() + " test(s).");

        if (features.isEmpty() && !logs.isEmpty()) {
            warnings.add("Warning: Logs were recorded but no test contexts were provided — reports will "
                + "be empty. Ensure DiagrammedTestRun.TestContexts.Enqueue(TestContext.Current) is called "
                + "in every test's DisposeAsync().");
        }

        int unpaired = countUnpairedRequests(logs);
        if (unpaired > 0) {
            warnings.add("Warning: " + unpaired + " unpaired request(s) detected (no matching response "
                + "with same RequestResponseId).");
        }

        if (!features.isEmpty()) {
            Set<String> scenarioIds = new HashSet<>();
            for (Feature f : features) {
                for (Scenario s : f.scenarios()) {
                    scenarioIds.add(s.testId());
                }
            }
            long orphaned = distinctTestIds.stream().filter(id -> !scenarioIds.contains(id)).count();
            if (orphaned > 0) {
                warnings.add("Warning: " + orphaned + " orphaned test ID(s) in logs do not match any "
                    + "feature scenario.");
            }
        }

        if (spanCount == 0) {
            warnings.add("Warning: InternalFlowSpanStore has 0 spans — activity diagrams will be empty.");
        } else {
            warnings.add("InternalFlowSpanStore: " + spanCount + " span(s).");
        }

        return warnings;
    }

    private static int countUnpairedRequests(List<RequestResponseLog> logs) {
        Set<UUID> responseIds = logs.stream()
            .filter(l -> l.type() == RequestResponseType.RESPONSE)
            .map(RequestResponseLog::requestResponseId).collect(Collectors.toSet());
        return (int) logs.stream()
            .filter(l -> l.type() == RequestResponseType.REQUEST)
            .filter(r -> !responseIds.contains(r.requestResponseId())).count();
    }
}
