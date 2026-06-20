plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J TestNG adapter — an ITestListener that scopes test identity (with mandatory " +
    "clearing) and records scenarios, plus IExecutionListener that finalizes the report once per JVM. " +
    "Registered via ServiceLoader. The user's project brings TestNG (compileOnly)."

dependencies {
    api(project(":kronikol4j-runtime"))
    compileOnly("org.testng:testng:7.10.2")
    testImplementation("org.testng:testng:7.10.2")
}
