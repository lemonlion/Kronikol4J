package io.kronikol.report.model;

import java.util.List;

/**
 * A group of related scenarios (typically one test class), mirroring the .NET {@code Feature}.
 * {@code endpoint}/{@code description}/{@code labels} are optional report-data metadata.
 */
public record Feature(String displayName, List<Scenario> scenarios,
                      String endpoint, String description, List<String> labels) {

    public Feature {
        scenarios = List.copyOf(scenarios);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /** Back-compatible feature (display name + scenarios, no metadata). */
    public Feature(String displayName, List<Scenario> scenarios) {
        this(displayName, scenarios, null, null, List.of());
    }
}
