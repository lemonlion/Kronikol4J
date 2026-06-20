plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J diagram pipeline — turns tracked logs into PlantUML DSL text " +
    "(sequence/component), plus the PlantUML text encoder and report data models. Pure logic, " +
    "no runtime dependencies (the canonical JSON note formatter is hand-rolled for parity, §6.4)."

dependencies {
    api(project(":kronikol4j-core"))
}
