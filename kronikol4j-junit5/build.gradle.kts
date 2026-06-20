plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J JUnit 5 adapter — an Extension that scopes test identity (with mandatory " +
    "clearing) and records scenario results, plus a ServiceLoader-registered LauncherSessionListener " +
    "that finalizes the report once per JVM (plan §5.4)."

dependencies {
    api(project(":kronikol4j-runtime"))
    // The user's test project brings JUnit; we compile against it but don't force a version.
    compileOnly("org.junit.jupiter:junit-jupiter-api:5.11.4")
    compileOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
