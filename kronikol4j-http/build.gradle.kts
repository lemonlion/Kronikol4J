plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J HTTP tracker — records outgoing HTTP exchanges as tracked interactions " +
    "(the LogPair auto-resolve ingestion pattern). Provides a recorder that OkHttp/WebClient/" +
    "RestTemplate interceptors delegate to. Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
