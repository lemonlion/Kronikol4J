plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J in-process message-bus tracker — the Java analog of the .NET MassTransit " +
    "extension (bus observer hooks). Classifies Send/Publish/Consume(+Fault) operations and records them as " +
    "event-styled interactions (MessageQueue category -> queue shape). Pure logic + a recorder + a Spring " +
    "ApplicationEvent listener binding. Core is required; Spring is compileOnly (the user brings it)."

dependencies {
    api(project(":kronikol4j-core"))
    // Spring ApplicationEvent binding (KronikolEventBusListener) — the user brings Spring.
    compileOnly("org.springframework:spring-context:6.1.14")
    testImplementation("org.springframework:spring-context:6.1.14")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
