plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J AWS adapters — records S3, DynamoDB, SQS and SNS operations as tracked " +
    "interactions. The recorders/classifiers are pure (no AWS SDK dependency); an AWS SDK v2 " +
    "ExecutionInterceptor delegates to them (the SDK is compileOnly — the user brings it)."

dependencies {
    api(project(":kronikol4j-core"))
    // The AWS SDK v2 ExecutionInterceptor SPI + SdkHttpRequest (the user brings the AWS SDK).
    compileOnly("software.amazon.awssdk:sdk-core:2.28.11")
    compileOnly("software.amazon.awssdk:http-client-spi:2.28.11")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("software.amazon.awssdk:sdk-core:2.28.11")
    testImplementation("software.amazon.awssdk:http-client-spi:2.28.11")
    // Cross-runtime end-to-end capture parity vs a live LocalStack S3/SQS (driven through the real AWS SDK v2).
    testImplementation("software.amazon.awssdk:s3:2.28.11")
    testImplementation("software.amazon.awssdk:sqs:2.28.11")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
}
