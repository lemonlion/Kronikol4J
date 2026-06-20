plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J report assembly — turns tracked logs + scenarios into an interactive " +
    "browser-rendered HTML report (and the mergeable JSON fragment). Embeds the diagram data map " +
    "for client-side PlantUML rendering."

dependencies {
    api(project(":kronikol4j-diagram"))
}
