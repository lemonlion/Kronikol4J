package io.kronikol.report.ci;

/**
 * One diagram part for the CI summary, mirroring the .NET {@code DefaultDiagramsFetcher.DiagramAsCode}
 * fields the markdown generator reads: the owning test's runtime id and the PlantUML source. A single
 * test may own several parts (client-side splitting), so these are carried as a flat list and grouped
 * by {@link #testId()} for lookup.
 *
 * @param testId     the owning test's runtime id (.NET {@code TestRuntimeId})
 * @param codeBehind the diagram's PlantUML source (.NET {@code CodeBehind})
 */
public record CiDiagram(String testId, String codeBehind) {
}
