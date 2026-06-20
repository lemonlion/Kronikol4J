plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J assertion tracking Tier 1 (plan §3.9) — zero bytecode weaving. Captures " +
    "assertion outcomes via AssertJ's native hooks: per-failure soft-assertion collection and the " +
    "global description consumer. The user brings AssertJ (compileOnly)."

dependencies {
    api(project(":kronikol4j-core"))
    compileOnly("org.assertj:assertj-core:3.27.3")
    // test classpath gets assertj-core from the convention plugin's test suite.
}
