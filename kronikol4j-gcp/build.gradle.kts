plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Google Cloud adapters — records BigQuery, Pub/Sub and Cloud Storage operations " +
    "as tracked interactions. Pure recorders/classifiers; a google-http-client response interceptor feeds the " +
    "BigQuery/Cloud Storage (HTTP) recorders (the SDK is compileOnly — the user brings it)."

dependencies {
    api(project(":kronikol4j-core"))
    // The google-http-client HttpResponseInterceptor SPI for the BigQuery/Cloud Storage HTTP hook.
    compileOnly("com.google.http-client:google-http-client:1.44.2")
    // The Pub/Sub (gRPC) client types the TrackingPublisher/TrackingMessageReceiver wrappers decorate.
    compileOnly("com.google.cloud:google-cloud-pubsub:1.131.0")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
