plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J assertion tracking Tier 2 (plan §3.9) — a ByteBuddy agent that instruments " +
    "AssertJ to auto-capture each assertion's actual + expected values AND its source expression, " +
    "with NO wrapper and NO .as(). The full-fidelity capture .NET achieves via IL weaving."

dependencies {
    api(project(":kronikol4j-core"))
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")
    // assertj is supplied to the test source set by the convention plugin.
}

// Self-attach is disabled by default on modern JDKs; ByteBuddy 1.15 needs the experimental flag
// to instrument classes whose generic signatures reference JDK 25 types.
tasks.withType<Test>().configureEach {
    jvmArgs("-Djdk.attach.allowAttachSelf=true", "-Dnet.bytebuddy.experimental=true")
}

// Allow this jar to be used as a real -javaagent (Premain/Agent + retransform capability).
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Premain-Class" to "io.kronikol.assertj.agent.KronikolAssertionAgent",
            "Agent-Class" to "io.kronikol.assertj.agent.KronikolAssertionAgent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true",
        )
    }
}
