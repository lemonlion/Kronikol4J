plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Azure adapters — records Cosmos DB, Blob Storage and Service Bus operations " +
    "as tracked interactions. Pure recorders (no Azure SDK dependency); an Azure SDK pipeline policy " +
    "delegates to them. Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
