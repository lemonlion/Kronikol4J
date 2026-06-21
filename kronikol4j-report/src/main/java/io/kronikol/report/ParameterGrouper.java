package io.kronikol.report;

import io.kronikol.report.model.Scenario;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups parameterized test scenarios for the report's parameter table (.NET {@code ParameterGrouper}).
 *
 * <p>Scope: grouping is by the framework-provided {@code outlineId} first, then by display-name prefix
 * ({@link ParameterParser#extractBaseName}); a group's columns come from {@code exampleValues} or, when
 * absent, from parsing each member's display name ({@link ParameterParser#parse}). A single
 * record-{@code ToString()} param is flattened into columns ({@code FlattenedObject}); complex-object
 * cells whose {@code exampleValues} string is a record shape render as R3 sub-tables / R4 expandables
 * via the string-based {@link ParameterValueRenderer}; {@code exampleFlatValues} drives the flatten
 * toggle. The one .NET path not ported is the reflection R2/R3/R4 rendering over a live
 * {@code ExampleRawValues} object graph — it is runtime-specific (PascalCase names, .NET type names)
 * and not cross-runtime byte-parity-able, so only the deterministic string-based subset is mirrored.
 */
final class ParameterGrouper {

    enum Rule {
        SCALAR_COLUMNS, FLATTENED_OBJECT, FALLBACK
    }

    /**
     * @param flatParameterNames the original (un-flattened) Gherkin example columns, present only when
     *        all members carry {@code exampleFlatValues}; drives the flatten toggle. Empty otherwise.
     * @param identical true when every member shares the same (non-empty) sequence diagram, so the group
     *        renders one shared diagram with an "identical across test cases" badge (.NET
     *        {@code AllDiagramsIdentical}); otherwise per-example diagrams are rendered.
     */
    record ParameterizedGroup(String groupDisplayName, List<String> parameterNames, Rule rule,
                              List<Scenario> scenarios, List<String> flatParameterNames, boolean identical) {
    }

    private ParameterGrouper() {
    }

    /** Returns the parameterized groups; non-grouped scenarios render individually. Groups by the
     *  framework-provided {@code outlineId} first, then — when {@code enabled} — by display-name prefix
     *  for the remainder (.NET {@code groupParameterizedTests} gates only this second pass). Preserves
     *  first-seen group order. */
    static List<ParameterizedGroup> analyze(List<Scenario> scenarios, int maxColumns,
                                            Map<String, String> diagramByTestId, boolean enabled) {
        List<ParameterizedGroup> groups = new ArrayList<>();
        if (scenarios.isEmpty()) {
            return groups;
        }
        Set<String> consumed = new HashSet<>();

        // 1. Group by OutlineId (framework-provided).
        Map<String, List<Scenario>> byOutline = new LinkedHashMap<>();
        for (Scenario s : scenarios) {
            if (s.outlineId() != null) {
                byOutline.computeIfAbsent(s.outlineId(), k -> new ArrayList<>()).add(s);
            }
        }
        for (Map.Entry<String, List<Scenario>> e : byOutline.entrySet()) {
            List<Scenario> members = e.getValue();
            if (members.size() >= 2 || hasParameters(members)) {
                groups.add(buildGroup(Humanize.formatScenarioDisplayName(e.getKey()), members, maxColumns,
                    diagramByTestId));
            }
            for (Scenario m : members) {
                consumed.add(m.testId());
            }
        }

        // 2. Group remaining scenarios by display-name prefix (.NET ParameterParser.ExtractBaseName) —
        // only when enabled; otherwise the non-OutlineId scenarios render individually.
        if (!enabled) {
            return groups;
        }
        Map<String, List<Scenario>> byPrefix = new LinkedHashMap<>();
        for (Scenario s : scenarios) {
            if (consumed.contains(s.testId())) {
                continue;
            }
            String baseName = ParameterParser.extractBaseName(s.name());
            if (baseName == null) {
                baseName = s.name();
            }
            byPrefix.computeIfAbsent(baseName, k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, List<Scenario>> e : byPrefix.entrySet()) {
            List<Scenario> members = e.getValue();
            if (members.size() >= 2 || hasParameters(members)) {
                groups.add(buildGroup(e.getKey(), members, maxColumns, diagramByTestId));
            } // else: single, parameter-less → rendered as an individual scenario
        }
        return groups;
    }

    private static boolean hasParameters(List<Scenario> members) {
        for (Scenario m : members) {
            if (m.exampleValues() != null && !m.exampleValues().isEmpty()) {
                return true;
            }
            Map<String, String> parsed = ParameterParser.parse(m.name());
            if (parsed != null && !parsed.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static ParameterizedGroup buildGroup(String groupName, List<Scenario> members, int maxColumns,
                                                 Map<String, String> diagramByTestId) {
        // The flat (un-flattened) Gherkin example columns are shown via the flatten toggle whenever all
        // members carry exampleFlatValues — independent of which display rule the grouped view uses.
        List<String> flatNames = computeFlatParameterNames(members);
        // Whether all members share one identical (non-empty) diagram → render it once with a badge.
        boolean identical = allDiagramsIdentical(members, diagramByTestId);

        // ExampleDisplayName forces the fallback single-column ("Test Case") layout.
        for (Scenario m : members) {
            if (m.exampleDisplayName() != null) {
                return new ParameterizedGroup(groupName, List.of(), Rule.FALLBACK, members, flatNames, identical);
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
                ParameterizedGroup flat = tryStringBasedFlatten(groupName, members, keys.iterator().next(),
                    maxColumns, flatNames, identical);
                if (flat != null) {
                    return flat;
                }
            }
            if (keys.size() > maxColumns) {
                return new ParameterizedGroup(groupName, List.of(), Rule.FALLBACK, members, flatNames, identical);
            }
            return new ParameterizedGroup(groupName, new ArrayList<>(keys), Rule.SCALAR_COLUMNS, members, flatNames, identical);
        }
        // Not all members carry ExampleValues → parse display names (.NET DetermineParamsAndRule
        // fallback). Every member's display name must parse, else the single "Test Case" column.
        List<Map<String, String>> allParsed = new ArrayList<>();
        for (Scenario m : members) {
            Map<String, String> parsed = ParameterParser.parse(m.name());
            if (parsed == null || parsed.isEmpty()) {
                return new ParameterizedGroup(groupName, List.of(), Rule.FALLBACK, members, flatNames, identical);
            }
            allParsed.add(parsed);
        }
        // Populate exampleValues from the parsed display name where absent; columns are the parsed keys.
        List<Scenario> populated = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        for (int i = 0; i < members.size(); i++) {
            Scenario m = members.get(i);
            allKeys.addAll(allParsed.get(i).keySet());
            populated.add(m.exampleValues() == null ? withExampleValues(m, allParsed.get(i)) : m);
        }
        if (allKeys.size() > maxColumns) {
            return new ParameterizedGroup(groupName, List.of(), Rule.FALLBACK, populated, flatNames, identical);
        }
        return new ParameterizedGroup(groupName, new ArrayList<>(allKeys), Rule.SCALAR_COLUMNS, populated, flatNames, identical);
    }

    /**
     * String-based R2 (.NET {@code ParameterGrouper.TryStringBasedFlatten}): when a single
     * {@code exampleValues} param is a record {@code ToString()} shape ("{@code Type { Prop = Val, ... }}")
     * that parses across <em>all</em> members with the first member's property names, flatten it into
     * one column per property — each member's {@code exampleValues} is replaced by the parsed map.
     * Returns null (no flatten) if any member fails to parse or lacks a property the first member has.
     */
    private static ParameterizedGroup tryStringBasedFlatten(String groupName, List<Scenario> members,
                                                            String paramKey, int maxColumns,
                                                            List<String> flatNames, boolean identical) {
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
        return new ParameterizedGroup(groupName, propertyNames, Rule.FLATTENED_OBJECT, flattened, flatNames, identical);
    }

    /**
     * Mirrors the .NET {@code diagramComparer}: true when there are ≥2 members, the first member has a
     * (non-null) diagram, and every member shares that identical diagram. With the Java single-diagram
     * model this is plain string equality (the .NET sorted-{@code CodeBehind} {@code SequenceEqual}).
     */
    private static boolean allDiagramsIdentical(List<Scenario> members, Map<String, String> diagramByTestId) {
        if (members.size() < 2 || diagramByTestId == null) {
            return false;
        }
        String first = diagramByTestId.get(members.get(0).testId());
        if (first == null) {
            return false;
        }
        for (int i = 1; i < members.size(); i++) {
            if (!first.equals(diagramByTestId.get(members.get(i).testId()))) {
                return false;
            }
        }
        return true;
    }

    /** The distinct {@code exampleFlatValues} keys across members, or empty if any member lacks them. */
    private static List<String> computeFlatParameterNames(List<Scenario> members) {
        for (Scenario m : members) {
            if (m.exampleFlatValues() == null || m.exampleFlatValues().isEmpty()) {
                return List.of();
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (Scenario m : members) {
            names.addAll(m.exampleFlatValues().keySet());
        }
        return new ArrayList<>(names);
    }

    /** Rebuilds an (immutable) {@link Scenario} with a replaced {@code exampleValues} map. */
    private static Scenario withExampleValues(Scenario s, Map<String, String> exampleValues) {
        return new Scenario(s.name(), s.testId(), s.status(), s.durationMs(), s.error(),
            s.isHappyPath(), s.errorStackTrace(), s.labels(), s.categories(), s.rule(),
            s.outlineId(), exampleValues, s.exampleFlatValues(), s.exampleDisplayName(),
            s.attachments(), s.backgroundSteps(), s.steps());
    }
}
