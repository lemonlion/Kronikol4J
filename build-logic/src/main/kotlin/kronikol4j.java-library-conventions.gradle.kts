// Shared conventions for every Kronikol4J library module.
// Java 17 baseline (compiled via --release so a single JDK 25 toolchain suffices).

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
    // -parameters keeps parameter names (used by reflection-based adapters);
    // -g keeps full debug info (LocalVariableTable) for the future assertion agent.
    options.compilerArgs.addAll(listOf("-parameters", "-g", "-Xlint:all,-serial,-processing,-options"))
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.11.4")
            dependencies {
                implementation("org.assertj:assertj-core:3.27.3")
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named<Jar>("jar") {
    val moduleSuffix = project.name.removePrefix("kronikol4j-").replace('-', '.')
    manifest {
        attributes(
            "Automatic-Module-Name" to "io.kronikol.$moduleSuffix",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString(),
        )
    }
}
