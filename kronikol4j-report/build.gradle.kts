plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J report assembly — turns tracked logs + scenarios into an interactive " +
    "browser-rendered HTML report (and the mergeable JSON fragment). Embeds the diagram data map " +
    "for client-side PlantUML rendering."

dependencies {
    api(project(":kronikol4j-diagram"))
}

// Generates the offline, self-contained report fixture (report.html + the PlantUML-WASM assets)
// that the Playwright pixel test renders in a real browser. Output: build/playwright/.
tasks.register<JavaExec>("generatePlaywrightFixture") {
    group = "verification"
    description = "Writes a self-contained offline report (+ WASM assets) for the Playwright render test."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.kronikol.report.tooling.OfflineReportFixture")
    args(layout.buildDirectory.dir("playwright").get().asFile.absolutePath)
    dependsOn("testClasses")
}
