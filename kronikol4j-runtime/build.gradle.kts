plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J run lifecycle — collects scenario results, finalizes the report at " +
    "end-of-run, and (across forked JVMs) emits/merges report fragments. Mode detection via the " +
    "kronikol.run.dir property (plan §5)."

dependencies {
    api(project(":kronikol4j-report"))
}
