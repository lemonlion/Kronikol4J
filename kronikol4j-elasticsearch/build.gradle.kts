plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Elasticsearch / OpenSearch tracker — records search/index operations as " +
    "tracked interactions (Elasticsearch category -> database shape). A client wrapper delegates to " +
    "it. Core only."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
