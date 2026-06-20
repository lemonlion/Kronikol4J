plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J servlet filter — server-side Layer-1 test-identity resolution (plan §7): " +
    "reads the test-identity headers off an incoming request and scopes them for request handling, " +
    "so tracking inside the system-under-test attributes to the calling test. Targets jakarta (§15)."

dependencies {
    api(project(":kronikol4j-core"))
    // The servlet container provides this; we never force a version.
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
}
