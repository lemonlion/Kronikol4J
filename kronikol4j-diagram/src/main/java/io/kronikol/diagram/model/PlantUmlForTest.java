package io.kronikol.diagram.model;

import io.kronikol.core.tracking.RequestResponseLog;
import java.util.List;

/**
 * The PlantUML diagram(s) generated for a single test. With browser-only client-side splitting
 * (plan §6.5) there is normally exactly one diagram per test; {@code diagrams} is a list to keep
 * the shape open.
 */
public record PlantUmlForTest(String testId, String testName, List<String> diagrams,
                              List<RequestResponseLog> traces) {

    public PlantUmlForTest {
        diagrams = List.copyOf(diagrams);
        traces = List.copyOf(traces);
    }
}
