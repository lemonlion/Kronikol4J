plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Azure Event Hubs tracker — classifies Event Hubs operations (Send/Read/Process/…) " +
    "and records them as tracked event interactions (MessageQueue category -> queue shape, event-styled). " +
    "Pure logic + a two-phase recorder + producer/consumer client wrappers (azure SDK compileOnly)."

dependencies {
    api(project(":kronikol4j-core"))
    // The Event Hubs (AMQP) client types the TrackingEventHubProducer/ConsumerClient wrappers decorate.
    compileOnly("com.azure:azure-messaging-eventhubs:5.18.0")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
