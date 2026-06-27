plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Maven plugin — a Mojo mirroring the Gradle plugin: a `report` goal that merges " +
    "the report fragments emitted by forked test JVMs into one HTML report (delegating to the same " +
    "MergeCommand engine as the kronikol4j merge CLI). Maven users' parity with the Gradle plugin."

dependencies {
    implementation(project(":kronikol4j-cli"))
    // The Maven plugin SPI (the user's Maven supplies these at runtime).
    compileOnly("org.apache.maven:maven-plugin-api:3.9.6")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.13.1")

    testImplementation("org.apache.maven:maven-plugin-api:3.9.6")
    testImplementation(project(":kronikol4j-runtime")) // to write a real fragment for the merge test
}

// Inject the build version into the hand-written Maven plugin descriptor (@version@), without touching the
// Maven ${...} expressions it also contains.
tasks.named<ProcessResources>("processResources") {
    filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("version" to project.version.toString()))
}
