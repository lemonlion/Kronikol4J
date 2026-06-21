plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Cassandra tracker — records CQL operations as tracked interactions " +
    "(Cassandra category -> database shape). A RequestTracker/wrapper delegates to it. Core only."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
