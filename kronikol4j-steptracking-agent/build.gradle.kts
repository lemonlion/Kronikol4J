plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J step tracking agent — a ByteBuddy agent that wraps methods annotated " +
    "@GivenStep/@WhenStep/@ThenStep/@ButStep/@Step with StepCollector.startStep/completeStep calls, " +
    "with NO source change. The runtime analog of .NET's StepTracking IL weaver."

dependencies {
    // StepCollector + the step annotations live in the report module; referenced by the inlined advice
    // (resolved on the user's runtime classpath) and the recorder, so compileOnly is enough.
    compileOnly(project(":kronikol4j-report"))
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")

    testImplementation(project(":kronikol4j-report"))
    testImplementation(project(":kronikol4j-core"))
}

// Self-attach + retransformation of already-loaded user classes on modern JDKs.
tasks.withType<Test>().configureEach {
    jvmArgs("-Djdk.attach.allowAttachSelf=true", "-Dnet.bytebuddy.experimental=true")
}

// Allow this jar to be used as a real -javaagent (Premain/Agent + retransform capability).
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Premain-Class" to "io.kronikol.steptracking.agent.KronikolStepTrackingAgent",
            "Agent-Class" to "io.kronikol.steptracking.agent.KronikolStepTrackingAgent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true",
        )
    }
}
