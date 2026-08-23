package io.kronikol.report.data;

import io.kronikol.core.tracking.DiagramMarkerKind;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Works out, for a scenario's ordered log stream, which step each interaction happened under, how long
 * each call took, and which markers carry information found nowhere else in the data file.
 *
 * <p>Ports the .NET {@code ReportGenerator.AttributeInteractionsToSteps} and
 * {@code ComputeInteractionDurations}. Attribution is positional — the <em>n</em>th step marker opens the
 * <em>n</em>th step in document order, background steps first — which is sound because the log store is
 * FIFO, so one test's records keep their relative order however many tests run in parallel. It is not
 * sound for a test doing work on a background thread, where a record can enqueue after the following
 * step's marker; the marker's text is therefore checked against the step's, and a disagreement yields no
 * {@code stepPath} rather than a confident wrong answer.
 */
final class InteractionAttribution {

    /** One scenario-level annotation: a marker whose content is not already in {@code steps}. */
    record Annotation(int index, DiagramMarkerKind kind, String text) {
    }

    /** Everything derived from one scenario's stream: paths aligned with its exported interactions. */
    record Result(List<String> stepPaths, List<Annotation> annotations, Map<UUID, Double> durations) {

        String pathAt(int index) {
            return index >= 0 && index < stepPaths.size() ? stepPaths.get(index) : null;
        }
    }

    private InteractionAttribution() {
    }

    /** The interactions a data file exports: everything that is not a diagram marker, in stream order. */
    static List<RequestResponseLog> interactions(List<RequestResponseLog> logs) {
        List<RequestResponseLog> out = new ArrayList<>();
        for (RequestResponseLog log : logs) {
            if (!log.diagramMarker()) {
                out.add(log);
            }
        }
        return out;
    }

    static Result attribute(List<RequestResponseLog> logs, Scenario scenario) {
        List<String> paths = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        List<String[]> ordered = orderedStepPaths(scenario);

        String current = null;
        int stepMarkers = 0;
        int interactionIndex = 0;

        for (RequestResponseLog log : logs) {
            if (!log.diagramMarker()) {
                paths.add(current);
                interactionIndex++;
                continue;
            }

            // The pair straddles the fragment; only the opening half carries it.
            if (!log.overrideStart() || log.plantUml() == null) {
                continue;
            }

            switch (log.markerKind()) {
                case STEP -> {
                    current = stepMarkers < ordered.size() && matches(log.plantUml(), ordered.get(stepMarkers)[1])
                        ? ordered.get(stepMarkers)[0]
                        : null;
                    stepMarkers++;
                }
                case ROW, CUSTOM -> annotations.add(
                    new Annotation(interactionIndex, log.markerKind(), annotationText(log.plantUml())));
                default -> {
                    // Step and assertion markers are already structured in `steps`.
                }
            }
        }

        return new Result(paths, annotations, durations(logs));
    }

    /**
     * Every step in the order its marker will arrive, paired with the address it gets in the data file:
     * {@code b0}, {@code b1} for background steps, then {@code 0}, {@code 1} for the scenario's own. Only
     * top-level steps appear — a step delimiter is emitted for those alone.
     */
    private static List<String[]> orderedStepPaths(Scenario scenario) {
        List<String[]> ordered = new ArrayList<>();
        if (scenario == null) {
            return ordered;
        }
        List<ScenarioStep> background = scenario.backgroundSteps();
        for (int i = 0; i < background.size(); i++) {
            ordered.add(new String[] { "b" + i, background.get(i).text() });
        }
        List<ScenarioStep> steps = scenario.steps();
        for (int i = 0; i < steps.size(); i++) {
            ordered.add(new String[] { String.valueOf(i), steps.get(i).text() });
        }
        return ordered;
    }

    /**
     * Whether a step delimiter's PlantUML belongs to a given step. The bar's label is the step text, but
     * possibly with the keyword prepended and the first letter capitalised, so this compares loosely: the
     * answer decides only whether positional attribution is trusted at all.
     */
    private static boolean matches(String plantUml, String stepText) {
        if (stepText == null || stepText.isBlank()) {
            return true;
        }
        return plantUml.replace('\n', ' ').trim().toLowerCase(Locale.ROOT)
            .contains(stepText.toLowerCase(Locale.ROOT));
    }

    /**
     * The readable half of an annotation marker. Falls back to the fragment as written when it is not a
     * one-line note, because a partially-parsed annotation is worse than a verbatim one.
     */
    private static String annotationText(String plantUml) {
        String text = plantUml.trim();
        int colon = text.indexOf(" : ");
        if (colon >= 0) {
            return text.substring(colon + 3).trim();
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(newline + 1).trim();
    }

    /**
     * Wall-clock duration per request/response pair. A capturer that measured the call itself is believed
     * over anything inferred here — it is the only source for a call sent as a single record.
     */
    private static Map<UUID, Double> durations(List<RequestResponseLog> logs) {
        Map<UUID, List<RequestResponseLog>> byPair = new LinkedHashMap<>();
        for (RequestResponseLog log : logs) {
            if (!log.diagramMarker()) {
                byPair.computeIfAbsent(log.requestResponseId(), k -> new ArrayList<>()).add(log);
            }
        }

        Map<UUID, Double> durations = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<RequestResponseLog>> pair : byPair.entrySet()) {
            Double measured = null;
            OffsetDateTime start = null;
            OffsetDateTime end = null;

            for (RequestResponseLog log : pair.getValue()) {
                if (measured == null && log.durationMs() != null) {
                    measured = log.durationMs();
                }
                if (log.type() == RequestResponseType.REQUEST && start == null) {
                    start = log.timestamp();
                } else if (log.type() == RequestResponseType.RESPONSE && end == null) {
                    end = log.timestamp();
                }
            }

            if (measured != null) {
                durations.put(pair.getKey(), measured);
                continue;
            }
            if (start == null || end == null) {
                continue;
            }
            double elapsed = Duration.between(start, end).toNanos() / 1_000_000.0;
            if (elapsed >= 0) {
                durations.put(pair.getKey(), elapsed);
            }
        }

        return durations;
    }
}
