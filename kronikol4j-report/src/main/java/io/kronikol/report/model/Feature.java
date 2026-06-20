package io.kronikol.report.model;

import java.util.List;

/** A group of related scenarios (typically one test class). */
public record Feature(String displayName, List<Scenario> scenarios) {

    public Feature {
        scenarios = List.copyOf(scenarios);
    }
}
