package io.kronikol.report.data;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.report.model.Feature;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The complete test-run report data serialized by {@link ReportDataSerializer} to JSON/XML/YAML —
 * the run metadata (Kronikol version + start/end times) plus the features, and the per-scenario
 * diagrams and tracked HTTP interactions.
 *
 * <p>{@code diagramsByTestId} / {@code logsByTestId} are keyed by scenario {@code testId}. A
 * {@code null} map means "not captured" (the section is omitted entirely); a non-null map means the
 * section is present (empty per scenario where there is nothing).
 *
 * @param kronikolVersion the version string emitted as {@code KronikolVersion} (caller-supplied for parity)
 */
public record ReportData(
    String kronikolVersion,
    Instant startTime,
    Instant endTime,
    List<Feature> features,
    Map<String, List<String>> diagramsByTestId,
    Map<String, List<RequestResponseLog>> logsByTestId) {
}
