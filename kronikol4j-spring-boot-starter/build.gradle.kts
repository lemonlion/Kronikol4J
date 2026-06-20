plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Spring Boot starter (plan §7) — auto-configuration that wires the servlet " +
    "identity filter and a RestTemplate tracking customizer, bound via @ConfigurationProperties " +
    "(prefix: kronikol). The user brings Spring Boot."

dependencies {
    api(project(":kronikol4j-spring"))
    api(project(":kronikol4j-servlet"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.5")
    compileOnly("org.springframework.boot:spring-boot:3.3.5")
    compileOnly("org.springframework:spring-web:6.1.14")
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")

    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.3.5")
    testImplementation("org.springframework.boot:spring-boot:3.3.5")
    testImplementation("org.springframework:spring-web:6.1.14")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
}
