// Kronikol4J component-test starter (JUnit 5 + Gradle).
// Copy this directory, rename the project, and replace the example service names below.
plugins {
    java
    // The Kronikol4J Gradle plugin: sets kronikol.run.dir on every Test task and finalizes the run into
    // one HTML report. See https://github.com/lemonlion/Kronikol4J/wiki
    id("io.kronikol.kronikol4j") version "0.1.25-SNAPSHOT"
}

repositories {
    mavenCentral()
    // Until Kronikol4J is on Maven Central, also enable the snapshot repo it is published to:
    // maven("https://...snapshots...")
}

val kronikol4jVersion = "0.1.25-SNAPSHOT"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // The JUnit 5 integration: auto-registers the report listener + the identity-scoping extension.
    testImplementation("io.github.lemonlion:kronikol4j-junit5:$kronikol4jVersion")
    // A tracking HTTP client (wrap any java.net.http.HttpClient). Swap for -jdbc / -redis / … as needed.
    testImplementation("io.github.lemonlion:kronikol4j-http:$kronikol4jVersion")
}

tasks.test {
    useJUnitPlatform()
}

// Optional report styling — see the README and the wiki for the full option surface.
kronikol {
    title = "MyApi Component Tests"
    arrowColors = true
    participantColors = true
}
