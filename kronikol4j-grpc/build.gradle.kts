plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J gRPC adapter — a ClientInterceptor that records gRPC calls as tracked " +
    "interactions (nearly 1:1 with the .NET gRPC interceptors). The recorder is testable without " +
    "gRPC; the interceptor compiles against grpc-api (compileOnly)."

dependencies {
    api(project(":kronikol4j-core"))
    compileOnly("io.grpc:grpc-api:1.68.0")
    // Protobuf→JSON message rendering (JsonFormat) — the user brings protobuf with their generated stubs.
    compileOnly("com.google.protobuf:protobuf-java-util:3.25.5")
    testImplementation("io.grpc:grpc-api:1.68.0")
    testImplementation("com.google.protobuf:protobuf-java-util:3.25.5")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
