plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Cucumber adapter — a Cucumber EventListener that scopes scenario identity " +
    "and maps Given/When/Then to Setup/Action phases (exercising TestPhaseContext). The user brings " +
    "Cucumber (compileOnly). Report finalization is shared via the JUnit Platform listener."

dependencies {
    api(project(":kronikol4j-runtime"))
    compileOnly("io.cucumber:cucumber-plugin:7.18.1")
    testImplementation("io.cucumber:cucumber-plugin:7.18.1")
}
