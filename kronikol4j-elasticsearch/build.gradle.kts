plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Elasticsearch / OpenSearch tracker — records search/index operations as " +
    "tracked interactions (Elasticsearch category -> database shape). An Apache HttpCore response " +
    "interceptor (the low-level RestClient's transport) feeds it; httpcore is compileOnly."

dependencies {
    api(project(":kronikol4j-core"))
    // The Apache HttpCore HttpResponseInterceptor SPI — the ES low-level RestClient's transport hook.
    compileOnly("org.apache.httpcomponents:httpcore:4.4.16")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("org.apache.httpcomponents:httpcore:4.4.16")
}
