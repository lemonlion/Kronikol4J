plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J MongoDB tracker — records Mongo operations as tracked interactions " +
    "(MongoDB category -> database shape). A command-listener wrapper delegates to it. Core only."

dependencies {
    api(project(":kronikol4j-core"))
    // BSON command documents the classifier inspects (the user brings the Mongo driver).
    compileOnly("org.mongodb:bson:5.1.4")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("org.mongodb:bson:5.1.4")
}
