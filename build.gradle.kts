// Root build. All shared configuration lives in build-logic convention plugins
// (applied per-module), keeping individual module build files trivial.

tasks.register("printModules") {
    description = "Lists the Kronikol4J modules currently wired into the build."
    group = "help"
    val names = subprojects.map { it.name }.sorted()
    doLast {
        println("Kronikol4J modules (${names.size}):")
        names.forEach { println("  - $it") }
    }
}
