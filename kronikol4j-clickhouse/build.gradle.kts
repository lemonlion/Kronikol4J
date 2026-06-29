plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J ClickHouse tracker — wraps a ClickHouse JDBC DataSource so every statement is " +
    "recorded as a tracked interaction with ClickHouse defaults (ClickHouse category -> database shape, " +
    "clickhouse:// URI scheme). Builds on the JDBC module; no ClickHouse driver dependency (works on any " +
    "JDBC DataSource via the shared dynamic-proxy plumbing + the dialect-aware UnifiedSqlClassifier)."

dependencies {
    api(project(":kronikol4j-jdbc"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("com.h2database:h2:2.2.224")
    // Cross-runtime end-to-end capture parity vs a live ClickHouse (driven through the real ClickHouse JDBC
    // driver — jdbc brings clickhouse-client/data transitively; http-client supplies the HTTP transport).
    testImplementation("com.clickhouse:clickhouse-jdbc:0.6.5")
    testImplementation("com.clickhouse:clickhouse-http-client:0.6.5")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
}
