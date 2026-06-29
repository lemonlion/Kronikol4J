plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J MongoDB tracker — records Mongo operations as tracked interactions " +
    "(MongoDB category -> database shape). A command-listener wrapper delegates to it; an OkHttp " +
    "interceptor covers the Atlas Data API (REST) front. The user brings the Mongo driver / OkHttp."

dependencies {
    api(project(":kronikol4j-core"))
    // BSON command documents + the driver CommandListener/events (the user brings the Mongo driver).
    compileOnly("org.mongodb:bson:5.1.4")
    compileOnly("org.mongodb:mongodb-driver-core:5.1.4")
    // The OkHttp interceptor for the Atlas Data API (REST) front (the user brings OkHttp).
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("org.mongodb:bson:5.1.4")
    testImplementation("org.mongodb:mongodb-driver-core:5.1.4")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Cross-runtime end-to-end capture parity vs a live Mongo (driven through the real sync driver).
    testImplementation("org.mongodb:mongodb-driver-sync:5.1.4")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
}
