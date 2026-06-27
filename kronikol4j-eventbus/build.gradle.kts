plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J in-process message-bus tracker — the Java analog of the .NET MassTransit " +
    "extension (bus observer hooks). Classifies Send/Publish/Consume(+Fault) operations and records them as " +
    "event-styled interactions (MessageQueue category -> queue shape). Pure logic + a recorder; bind it to " +
    "Spring ApplicationEvents / Axon. Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
