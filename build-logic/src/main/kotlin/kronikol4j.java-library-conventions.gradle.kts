// Shared conventions for every Kronikol4J library module.
// Java 17 baseline (compiled via --release so a single JDK 25 toolchain suffices).
// Also publishes every module to Maven Central via the Sonatype Central Portal (the vanniktech plugin
// handles the sources/javadoc jars, GPG signing, upload, and release — all gated on the CI secrets).

import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
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

mavenPublishing {
    // Upload to the Sonatype Central Portal and auto-release once validation passes. Credentials come
    // from `mavenCentralUsername`/`mavenCentralPassword`; the GPG key from `signingInMemoryKey`/
    // `signingInMemoryKeyPassword` (all supplied as ORG_GRADLE_PROJECT_* env vars in CI). A plain
    // `build`/`test` runs no publish or sign task, so local builds need no key.
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates(project.group.toString(), project.name, project.version.toString())

    pom {
        name.set(project.name)
        description.set(provider { project.description ?: "Kronikol4J module" })
        url.set("https://github.com/lemonlion/Kronikol4J")
        licenses {
            license {
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
            developerConnection.set("scm:git:ssh://git@github.com/lemonlion/Kronikol4J.git")
        }
    }
}
