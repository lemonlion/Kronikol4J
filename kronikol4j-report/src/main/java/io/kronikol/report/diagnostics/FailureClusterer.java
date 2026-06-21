package io.kronikol.report.diagnostics;

import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Scenario;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups failed scenarios by normalised error message to surface common failure patterns — a port of the
 * .NET {@code FailureClusterer}. Only clusters of two or more are returned, ordered by size descending
 * (a stable order-by, so equal-size clusters keep first-appearance order).
 */
public final class FailureClusterer {

    private FailureClusterer() {
    }

    /** A group of scenarios sharing the same normalised error message. */
    public record FailureCluster(String clusterKey, List<Scenario> scenarios) {
    }

    public static List<FailureCluster> cluster(List<Scenario> scenarios) {
        Map<String, List<Scenario>> byKey = new LinkedHashMap<>();
        for (Scenario s : scenarios) {
            if (s.status() == ExecutionStatus.FAILED && s.error() != null) {
                byKey.computeIfAbsent(normalizeKey(s.error()), k -> new ArrayList<>()).add(s);
            }
        }
        List<FailureCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, List<Scenario>> e : byKey.entrySet()) {
            if (e.getValue().size() >= 2) {
                clusters.add(new FailureCluster(e.getKey(), e.getValue()));
            }
        }
        clusters.sort(Comparator.comparingInt((FailureCluster c) -> c.scenarios().size()).reversed());
        return clusters;
    }

    /** First line only, trimmed, with internal whitespace runs collapsed to a single space. */
    private static String normalizeKey(String errorMessage) {
        String firstLine = errorMessage.split("\n", -1)[0].strip();
        return firstLine.replaceAll("\\s+", " ").strip();
    }
}
