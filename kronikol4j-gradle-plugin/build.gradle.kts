plugins {
    `java-gradle-plugin`
}

description = "Kronikol4J Gradle plugin — wires forked-JVM report aggregation into the build: sets " +
    "kronikol.run.dir on test tasks so forks emit fragments, and registers a kronikolReport task that " +
    "merges them into one HTML report (plan §5.3)."

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("kronikol4j") {
            id = "io.kronikol.kronikol4j"
            implementationClass = "io.kronikol.gradle.KronikolPlugin"
            displayName = "Kronikol4J"
            description = "Aggregates Kronikol4J test report fragments into one HTML report."
        }
    }
}

dependencies {
    implementation(project(":kronikol4j-cli"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
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
