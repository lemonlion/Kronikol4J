plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Azure Event Hubs tracker — classifies Event Hubs operations (Send/Read/Process/…) " +
    "and records them as tracked event interactions (MessageQueue category -> queue shape, event-styled). " +
    "Pure logic + a two-phase recorder; depends only on core (the producer/consumer client wrappers feed it)."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
