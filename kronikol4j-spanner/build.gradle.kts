plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Spanner tracker — wraps a Cloud Spanner JDBC DataSource so every statement is " +
    "recorded as a tracked interaction with Spanner defaults (Spanner category -> database shape, " +
    "spanner:// URI scheme). Builds on the JDBC module; no Spanner driver dependency (works on any JDBC " +
    "DataSource via the shared dynamic-proxy plumbing + the dialect-aware UnifiedSqlClassifier)."

dependencies {
    api(project(":kronikol4j-jdbc"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("com.h2database:h2:2.2.224")
}
