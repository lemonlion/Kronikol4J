package io.kronikol.diagram.plantuml;

/**
 * Controls how GraphQL request bodies are displayed in sequence-diagram notes (.NET
 * {@code GraphQlBodyFormat}).
 */
public enum GraphQlBodyFormat {
    /** JSON pretty-print; the query value stays a single-line string (no GraphQL formatting). */
    JSON,
    /** Formatted GraphQL query only; HTTP headers and JSON metadata are suppressed. */
    FORMATTED_QUERY_ONLY,
    /** Formatted GraphQL query with HTTP headers shown above. */
    FORMATTED,
    /** Formatted query + headers + variables/extensions sections below. The .NET default. */
    FORMATTED_WITH_METADATA
}
