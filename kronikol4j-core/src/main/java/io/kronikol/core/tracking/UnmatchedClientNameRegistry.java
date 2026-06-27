package io.kronikol.core.tracking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A process-wide diagnostic registry of HTTP {@code clientName} values that were supplied to a tracker but
 * did not match any key in its {@code clientNamesToServiceNames} map (so the tracker fell back to port-based
 * naming). The diagnostic report surfaces these as configuration mismatches. Java port of the .NET
 * {@code UnmatchedClientNameRegistry}.
 *
 * <p>Wire it into a tracker via the {@link io.kronikol.core.naming.ServiceNameResolver}
 * {@code onUnmatchedClientName} seam, e.g. {@code .onUnmatchedClientName(UnmatchedClientNameRegistry::record)}.
 * Thread-safe; iteration order is insertion order so equal counts sort stably (matching .NET's stable
 * {@code OrderByDescending}).
 */
public final class UnmatchedClientNameRegistry {

    /** A recorded unmatched client name and how many requests used it. */
    public record RecordedName(String clientName, int requestCount) {
    }

    private static final Object LOCK = new Object();
    private static Map<String, Integer> names = new LinkedHashMap<>();

    private UnmatchedClientNameRegistry() {
    }

    /** Records a client name that failed to match any {@code clientNamesToServiceNames} key. */
    public static void record(String clientName) {
        if (clientName == null) {
            return;
        }
        synchronized (LOCK) {
            names.merge(clientName, 1, Integer::sum);
        }
    }

    /** All recorded unmatched client names with their request counts, ordered by count descending. */
    public static List<RecordedName> getRecordedNames() {
        synchronized (LOCK) {
            List<RecordedName> out = new ArrayList<>(names.size());
            names.forEach((name, count) -> out.add(new RecordedName(name, count)));
            // Stable sort by count descending — ties keep insertion order (matches .NET OrderByDescending).
            out.sort((a, b) -> Integer.compare(b.requestCount(), a.requestCount()));
            return List.copyOf(out);
        }
    }

    /** Clears all recorded names. */
    public static void clear() {
        synchronized (LOCK) {
            names = new LinkedHashMap<>();
        }
    }
}
