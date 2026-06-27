package io.kronikol.report.flow;

import java.util.List;
import java.util.TreeSet;

/**
 * Discovers the instrumentation-scope ("activity source") names present in the captured spans — the diagnostic
 * counterpart to the .NET {@code ActivitySourceDiscovery}. .NET reflects over the process's registered
 * {@code ActivitySource}s; Java has no such global registry, so the portable equivalent is the distinct source
 * names actually seen in {@link InternalFlowSpanStore} (which is what the diagnostic report needs to surface).
 */
public final class ActivitySourceDiscovery {

    private ActivitySourceDiscovery() {
    }

    /** The distinct, sorted instrumentation-scope names across the captured spans. */
    public static List<String> discoveredSources() {
        TreeSet<String> names = new TreeSet<>();
        for (InternalFlowSpan span : InternalFlowSpanStore.getSpans()) {
            if (span.sourceName() != null && !span.sourceName().isEmpty()) {
                names.add(span.sourceName());
            }
        }
        return List.copyOf(names);
    }
}
