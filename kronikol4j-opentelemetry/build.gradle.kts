plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J OpenTelemetry bridge (plan §3.8) — stamps the current span's trace/span " +
    "ids onto tracked logs (the .NET System.Diagnostics.Activity mapping), so diagrams correlate " +
    "with distributed traces. The OTel API stays out of core; the user brings it (compileOnly)."

dependencies {
    api(project(":kronikol4j-core"))
    // For the InternalFlow span-capture bridge (projects OTel spans into InternalFlowSpan + the store).
    implementation(project(":kronikol4j-report"))
    compileOnly("io.opentelemetry:opentelemetry-api:1.43.0")
    // The SpanProcessor SPI the capture bridge implements (the user brings the OTel SDK).
    compileOnly("io.opentelemetry:opentelemetry-sdk:1.43.0")
    testImplementation("io.opentelemetry:opentelemetry-sdk:1.43.0")
}
