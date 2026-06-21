package io.kronikol.runtime;

import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects scenario results during a run, grouped by feature (test class), preserving encounter
 * order. Test-framework adapters call {@link #record}; {@link ReportFinalizer} reads {@link #toFeatures()}.
 * Thread-safe for parallel test execution within one JVM (plan §15).
 */
public final class RunResults {

    private static final Map<String, List<Scenario>> BY_FEATURE = new LinkedHashMap<>();
    private static final Object LOCK = new Object();
    private static Instant startedAt;

    private RunResults() {
    }

    public static void record(String featureName, Scenario scenario) {
        synchronized (LOCK) {
            if (startedAt == null) {
                startedAt = Instant.now(); // run start = first recorded scenario
            }
            BY_FEATURE.computeIfAbsent(featureName, k -> new ArrayList<>()).add(scenario);
        }
    }

    /** When the run started (the first {@link #record} call), or {@code now} if nothing was recorded. */
    public static Instant startedAt() {
        synchronized (LOCK) {
            return startedAt != null ? startedAt : Instant.now();
        }
    }

    public static List<Feature> toFeatures() {
        synchronized (LOCK) {
            List<Feature> features = new ArrayList<>(BY_FEATURE.size());
            BY_FEATURE.forEach((name, scenarios) -> features.add(new Feature(name, scenarios)));
            return features;
        }
    }

    public static boolean isEmpty() {
        synchronized (LOCK) {
            return BY_FEATURE.isEmpty();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            BY_FEATURE.clear();
            startedAt = null;
        }
    }
}
