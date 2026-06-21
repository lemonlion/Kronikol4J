package io.kronikol.report;

import io.kronikol.report.model.Scenario;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups parameterized test scenarios for the report's parameter table (.NET {@code ParameterGrouper}).
 *
 * <p>Scope: the Java {@link Scenario} model carries {@code outlineId} / {@code exampleValues} /
 * {@code exampleDisplayName} but not the .NET {@code ExampleRawValues}/{@code ExampleFlatValues}. So
 * grouping is by the framework-provided {@code outlineId} and the table is rendered with the
 * {@code ScalarColumns} (or {@code Fallback}) rule. The .NET display-name-prefix grouping (via
 * {@code ParameterParser}), R2 flattened-object detection, the flatten toggle and complex-object
 * cells are not representable in the Java model and are intentionally out of scope.
 */
final class ParameterGrouper {

    enum Rule {
        SCALAR_COLUMNS, FALLBACK
    }

    record ParameterizedGroup(String groupDisplayName, List<String> parameterNames, Rule rule,
                              List<Scenario> scenarios) {
    }

    private ParameterGrouper() {
    }

    /** Returns the parameterized groups (keyed by {@code outlineId}); non-grouped scenarios render
     *  individually. Preserves first-seen group order. */
    static List<ParameterizedGroup> analyze(List<Scenario> scenarios, int maxColumns) {
        List<ParameterizedGroup> groups = new ArrayList<>();
        if (scenarios.isEmpty()) {
            return groups;
        }
        Map<String, List<Scenario>> byOutline = new LinkedHashMap<>();
        for (Scenario s : scenarios) {
            if (s.outlineId() != null) {
                byOutline.computeIfAbsent(s.outlineId(), k -> new ArrayList<>()).add(s);
            }
        }
        for (Map.Entry<String, List<Scenario>> e : byOutline.entrySet()) {
            List<Scenario> members = e.getValue();
            if (members.size() < 2 && !hasParameters(members)) {
                continue; // single, parameter-less → rendered as an individual scenario
            }
            groups.add(buildGroup(Humanize.formatScenarioDisplayName(e.getKey()), members, maxColumns));
        }
        return groups;
    }

    private static boolean hasParameters(List<Scenario> members) {
        for (Scenario m : members) {
            if (m.exampleValues() != null && !m.exampleValues().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static ParameterizedGroup buildGroup(String groupName, List<Scenario> members, int maxColumns) {
        // ExampleDisplayName forces the fallback single-column ("Test Case") layout.
        for (Scenario m : members) {
            if (m.exampleDisplayName() != null) {
                return new ParameterizedGroup(groupName, List.of(), Rule.FALLBACK, members);
            }
        }
        int withValues = 0;
        for (Scenario m : members) {
            if (m.exampleValues() != null && !m.exampleValues().isEmpty()) {
                withValues++;
            }
        }
        if (withValues == members.size()) {
            Set<String> keys = new LinkedHashSet<>();
            for (Scenario m : members) {
                keys.addAll(m.exampleValues().keySet());
            }
            if (keys.size() > maxColumns) {
                return new ParameterizedGroup(groupName, List.of(), Rule.FALLBACK, members);
            }
            return new ParameterizedGroup(groupName, new ArrayList<>(keys), Rule.SCALAR_COLUMNS, members);
        }
        // Not all members carry ExampleValues → .NET parses display names (ParameterParser, out of
        // scope here); fall back to the single "Test Case" column.
        return new ParameterizedGroup(groupName, List.of(), Rule.FALLBACK, members);
    }
}
