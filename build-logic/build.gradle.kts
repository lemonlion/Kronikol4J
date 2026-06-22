plugins {
    `kotlin-dsl`
}

// Convention plugins shared by every Kronikol4J module live here.

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Lets the convention plugin apply the Central Portal publisher (upload + signing) via `id(...)`.
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.30.0")
}
