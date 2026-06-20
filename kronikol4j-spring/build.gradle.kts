plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Spring integration (plan §7) — a RestTemplate ClientHttpRequestInterceptor " +
    "that records outgoing HTTP exchanges (delegating to the HTTP recorder). Targets jakarta/Spring 6 " +
    "(compileOnly); the user brings Spring."

dependencies {
    api(project(":kronikol4j-http"))
    compileOnly("org.springframework:spring-web:6.1.14")
    testImplementation("org.springframework:spring-web:6.1.14")
    testImplementation("org.springframework:spring-test:6.1.14")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
