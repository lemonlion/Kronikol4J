plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J generic proxy tracker — wraps any interface with a java.lang.reflect.Proxy " +
    "that records each method call as a tracked interaction (the .NET DispatchProxy pattern). Core only; " +
    "opentelemetry-api is compileOnly (optional per-call span emission for InternalFlow)."

dependencies {
    api(project(":kronikol4j-core"))
    // Optional: emit an OpenTelemetry span per tracked call (activitySourceName) for InternalFlow capture.
    compileOnly("io.opentelemetry:opentelemetry-api:1.43.0")
    // End-to-end test drives the proxy under the real JUnit extension and renders a diagram.
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("io.opentelemetry:opentelemetry-api:1.43.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk:1.43.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.43.0")
}
