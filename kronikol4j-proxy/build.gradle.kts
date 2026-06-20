plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J generic proxy tracker — wraps any interface with a java.lang.reflect.Proxy " +
    "that records each method call as a tracked interaction (the .NET DispatchProxy pattern). " +
    "Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    // End-to-end test drives the proxy under the real JUnit extension and renders a diagram.
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
