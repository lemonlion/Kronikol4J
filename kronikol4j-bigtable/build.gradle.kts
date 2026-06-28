plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Bigtable tracker — classifies Cloud Bigtable operations (ReadRows/MutateRow/…) " +
    "and records them as tracked interactions (Bigtable category -> database shape). Pure logic + a " +
    "two-phase recorder + a gRPC ClientInterceptor (grpc-api compileOnly) that feeds it."

dependencies {
    api(project(":kronikol4j-core"))
    // The gRPC ClientInterceptor SPI — the Bigtable data client's transport (gax-grpc) hook.
    compileOnly("io.grpc:grpc-api:1.68.0")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("io.grpc:grpc-api:1.68.0")
}
