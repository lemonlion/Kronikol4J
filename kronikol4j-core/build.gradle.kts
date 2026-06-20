plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J core — the stable tracking seam (data model, logger, context, " +
    "correlation, registry, constants). Zero runtime dependencies beyond the JDK."

// HARD RULE (plan §15): kronikol4j-core has NO required runtime dependencies.
// Do not add an `implementation`/`api` dependency on any tracked technology here.
