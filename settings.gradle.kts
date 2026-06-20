pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kronikol4j"

// Modules are added here as each is created (keeps the build green at every step).
include(
    "kronikol4j-core",
    "kronikol4j-diagram",
)
