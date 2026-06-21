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
 * {@code exampleDisplayName} but not the .NET {@code ExampleRawValues} (a live {@code object} graph —
 * the reflection R2/R3/R4 paths over it are runtime-specific and not cross-runtime byte-parity-able).
 * So grouping is by the framework-provided {@code outlineId} and the table is rendered with the
 * {@code ScalarColumns} (or {@code Fallback}) rule. Complex-object cells whose {@code exampleValues}
 * string is a record {@code ToString()} shape ARE rendered (R3 sub-table / R4 expandable) via the
 * string-based {@link ParameterValueRenderer}. The .NET display-name-prefix grouping (via
 * {@code ParameterParser}) and the string-based R2 flatten toggle are handled separately.
 */
final class ParameterGrouper {

    enum Rule {
        SCALAR_COLUMNS, FLATTENED_OBJECT, FALLBACK
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
            // R2: a single param whose value is a record ToString() string → flatten into columns.
            if (keys.size() == 1) {
                ParameterizedGroup flat = tryStringBasedFlatten(groupName, members, keys.iterator().next(), maxColumns);
                if (flat != null) {
                    return flat;
                }
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

    /**
     * String-based R2 (.NET {@code ParameterGrouper.TryStringBasedFlatten}): when a single
     * {@code exampleValues} param is a record {@code ToString()} shape ("{@code Type { Prop = Val, ... }}")
     * that parses across <em>all</em> members with the first member's property names, flatten it into
     * one column per property — each member's {@code exampleValues} is replaced by the parsed map.
     * Returns null (no flatten) if any member fails to parse or lacks a property the first member has.
     */
    private static ParameterizedGroup tryStringBasedFlatten(String groupName, List<Scenario> members,
                                                            String paramKey, int maxColumns) {
        String firstValue = members.get(0).exampleValues() == null
            ? null : members.get(0).exampleValues().get(paramKey);
        Map<String, String> firstParsed = ParameterParser.tryParseRecordToString(firstValue);
        if (firstParsed == null) {
            return null;
        }
        List<String> propertyNames = new ArrayList<>(firstParsed.keySet());
        if (propertyNames.isEmpty() || propertyNames.size() > maxColumns) {
            return null;
        }
        List<Scenario> flattened = new ArrayList<>();
        for (Scenario m : members) {
            String val = m.exampleValues() == null ? null : m.exampleValues().get(paramKey);
            Map<String, String> parsed = ParameterParser.tryParseRecordToString(val);
            if (parsed == null) {
                return null;
            }
            for (String pn : propertyNames) {
                if (!parsed.containsKey(pn)) {
                    return null;
                }
            }
            flattened.add(withExampleValues(m, parsed));
        }
        return new ParameterizedGroup(groupName, propertyNames, Rule.FLATTENED_OBJECT, flattened);
    }

    /** Rebuilds an (immutable) {@link Scenario} with a replaced {@code exampleValues} map. */
    private static Scenario withExampleValues(Scenario s, Map<String, String> exampleValues) {
        return new Scenario(s.name(), s.testId(), s.status(), s.durationMs(), s.error(),
            s.isHappyPath(), s.errorStackTrace(), s.labels(), s.categories(), s.rule(),
            s.outlineId(), exampleValues, s.exampleDisplayName(),
            s.attachments(), s.backgroundSteps(), s.steps());
    }
}
