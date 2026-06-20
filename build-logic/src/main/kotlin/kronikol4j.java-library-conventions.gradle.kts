// Shared conventions for every Kronikol4J library module.
// Java 17 baseline (compiled via --release so a single JDK 25 toolchain suffices).
// Also makes every module publishable to Maven Central (signing gated on keys — §11/§15).

plugins {
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
    // -parameters keeps parameter names (reflection-based adapters);
    // -g keeps full debug info (LocalVariableTable) for the future assertion agent.
    options.compilerArgs.addAll(listOf("-parameters", "-g", "-Xlint:all,-serial,-processing,-options"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    isFailOnError = false
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set(provider { project.description ?: "Kronikol4J module" })
                url.set("https://github.com/lemonlion/Kronikol4J")
                licenses {
                    license {
                        // TODO: reconcile with the .NET Kronikol license during release setup.
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("lemonlion")
                        name.set("Kronikol")
                    }
                }
                scm {
                    url.set("https://github.com/lemonlion/Kronikol4J")
                    connection.set("scm:git:https://github.com/lemonlion/Kronikol4J.git")
                }
            }
        }
    }
}

signing {
    // Sign only when a key is supplied (CI release); local builds publish unsigned.
    val signingKey = providers.gradleProperty("signingKey").orNull
    val signingPassword = providers.gradleProperty("signingPassword").orNull
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
