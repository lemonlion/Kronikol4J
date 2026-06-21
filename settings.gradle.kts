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
    "kronikol4j-report",
    "kronikol4j-runtime",
    "kronikol4j-junit5",
    "kronikol4j-proxy",
    "kronikol4j-http",
    "kronikol4j-jdbc",
    "kronikol4j-servlet",
    "kronikol4j-cli",
    "kronikol4j-redis",
    "kronikol4j-mongodb",
    "kronikol4j-messaging",
    "kronikol4j-testng",
    "kronikol4j-assertj",
    "kronikol4j-opentelemetry",
    "kronikol4j-spring",
    "kronikol4j-grpc",
    "kronikol4j-spring-boot-starter",
    "kronikol4j-cucumber",
    "kronikol4j-aws",
    "kronikol4j-azure",
    "kronikol4j-gcp",
    "kronikol4j-gradle-plugin",
    "kronikol4j-assertj-agent",
    "kronikol4j-cassandra",
    "kronikol4j-elasticsearch",
    "kronikol4j-spock",
)
