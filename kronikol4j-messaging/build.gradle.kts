plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J messaging tracker — records publish/consume as fire-and-forget Event " +
    "interactions (MessageQueue category -> queue shape). Kafka/JMS/RabbitMQ wrappers delegate to " +
    "it; producers/consumers also propagate identity via the kronikol-test-* message headers. Core only."

dependencies {
    api(project(":kronikol4j-core"))
    // The Kafka client the producer/consumer wrappers decorate (the user brings Kafka).
    compileOnly("org.apache.kafka:kafka-clients:3.7.1")
    // Spring Kafka + Spring beans for the zero-call-site BeanPostProcessor auto-wiring (the user brings them).
    compileOnly("org.springframework.kafka:spring-kafka:3.1.2")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("org.apache.kafka:kafka-clients:3.7.1")
    testImplementation("org.springframework.kafka:spring-kafka:3.1.2")
}
