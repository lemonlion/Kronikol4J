plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J CLI — the `merge` command (the 'Merging Parallel Reports' feature, plan " +
    "§5.5): reads enriched JSON fragments from sharded CI runners and combines them into one HTML " +
    "report. Distributed as a runnable fat-jar (run with `java -jar` or via jbang)."

dependencies {
    implementation(project(":kronikol4j-report"))
    testImplementation(project(":kronikol4j-runtime")) // to write a real fragment for the distribution test
}

// The runnable fat-jar (Main-Class + all runtime deps bundled) — the distribution artifact. Run with
// `java -jar kronikol4j-cli-<version>-all.jar merge …`, or via jbang from the published GAV (see the wiki).
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "io.kronikol.cli.Main")
    }
    from(sourceSets["main"].output)
    dependsOn(configurations.named("runtimeClasspath"))
    from({
        configurations.named("runtimeClasspath").get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("assemble") {
    dependsOn(fatJar)
}

// The distribution test runs the built fat-jar end-to-end, so it needs it built + its path.
tasks.named<Test>("test") {
    dependsOn(fatJar)
    systemProperty("kronikol.cli.fatjar", fatJar.get().archiveFile.get().asFile.absolutePath)
}
