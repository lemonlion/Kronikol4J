plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J AWS adapters — records S3, DynamoDB, SQS and SNS operations as tracked " +
    "interactions. The recorders are pure (no AWS SDK dependency); an AWS SDK ExecutionInterceptor " +
    "delegates to them. Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
