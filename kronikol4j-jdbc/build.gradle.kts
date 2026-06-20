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
}
