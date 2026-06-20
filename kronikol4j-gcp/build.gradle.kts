plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Google Cloud adapters — records BigQuery, Pub/Sub and Cloud Storage " +
    "operations as tracked interactions. Pure recorders (no GCP SDK dependency). Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
