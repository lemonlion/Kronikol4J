plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Azure adapters — records Cosmos DB, Blob Storage, Storage Queues and Service Bus " +
    "operations as tracked interactions. Pure recorders/classifiers (no Azure SDK dependency); an Azure SDK " +
    "HttpPipelinePolicy delegates to them (azure-core is compileOnly — the user brings it)."

dependencies {
    api(project(":kronikol4j-core"))
    // The Azure SDK HttpPipelinePolicy SPI + HttpRequest/HttpResponse (the user brings the Azure SDK).
    compileOnly("com.azure:azure-core:1.52.0")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("com.azure:azure-core:1.52.0")
}
