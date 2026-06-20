plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J CLI — the `merge` command (the 'Merging Parallel Reports' feature, plan " +
    "§5.5): reads enriched JSON fragments from sharded CI runners and combines them into one HTML " +
    "report. Distribute as a runnable jar / Gradle goal."

dependencies {
    implementation(project(":kronikol4j-report"))
}
