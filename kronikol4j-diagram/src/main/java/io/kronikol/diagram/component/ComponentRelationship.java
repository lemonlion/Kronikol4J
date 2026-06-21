package io.kronikol.diagram.component;

import java.util.Set;

/**
 * An aggregated caller→service dependency for the component diagram (mirrors the .NET
 * {@code ComponentRelationship}). One entry per distinct {@code (caller, service, protocol)} across
 * the run, carrying the set of methods used, the total call count, and the number of distinct tests
 * that exercised it.
 *
 * @param caller             the calling participant's name
 * @param service            the target service's name
 * @param protocol           the wire protocol / dependency category (e.g. {@code "HTTP"}, {@code "SQL"})
 * @param methods            the distinct methods used over this relationship
 * @param callCount          total request count
 * @param testCount          number of distinct tests that exercised it
 * @param dependencyCategory the first non-null dependency category seen (drives shape + colour); may be null
 */
public record ComponentRelationship(
    String caller,
    String service,
    String protocol,
    Set<String> methods,
    int callCount,
    int testCount,
    String dependencyCategory) {
}
