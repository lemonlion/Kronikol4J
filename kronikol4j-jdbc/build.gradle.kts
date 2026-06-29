plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J JDBC tracker — records SQL executions as tracked interactions (the direct " +
    "log() construction pattern, the .NET EF Core interceptor equivalent). One JDBC-level tracker " +
    "covers every relational database (plan §2). Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    // In-memory database for end-to-end DataSource-wrapping tests (the JDBC API itself is in the JDK).
    testImplementation("com.h2database:h2:2.2.224")
    // Cross-runtime end-to-end capture parity vs a live Postgres (driven through the real PG JDBC driver).
    testImplementation("org.postgresql:postgresql:42.7.4")
    // …and vs a live MySQL (the same generic TrackingDataSource, driven through the real MySQL JDBC driver).
    testImplementation("com.mysql:mysql-connector-j:8.4.0")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
}
