package io.kronikol.diagram.component;

/**
 * Controls whether component-diagram arrows are coloured by dependency type or by performance (P95 latency).
 * Java port of the .NET {@code ArrowColorMode}.
 */
public enum ArrowColorMode {

    /** Arrow colour indicates the target service's dependency type (default). */
    DEPENDENCY_TYPE,

    /** Arrow colour indicates P95 latency (green / orange / red). */
    PERFORMANCE
}
