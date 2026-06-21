plugins {
    id("kronikol4j.java-library-conventions")
    groovy // for the real Spock integration spec under src/test/groovy
}

description = "Kronikol4J Spock adapter — a global Spock extension that scopes feature-iteration " +
    "identity and maps Spock blocks (given/setup -> Setup, when/then/expect -> Action) to phases. " +
    "The user brings Spock (compileOnly). Report finalization is shared via the JUnit Platform listener."

dependencies {
    api(project(":kronikol4j-runtime"))
    compileOnly("org.spockframework:spock-core:2.3-groovy-4.0")
    // The real Spock spec exercises the global extension end-to-end (Spock supplies its own JUnit
    // Platform engine, discovered by the convention plugin's useJUnitPlatform()).
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
    // Spock 2.3 pulls Groovy 4.0.13, which cannot parse JDK 25 (class major 69) class files. Force a
    // Groovy that supports JDK 25 so the Groovy compiler/runtime works on the JDK 25 toolchain.
    testImplementation("org.apache.groovy:groovy:4.0.28")
}
