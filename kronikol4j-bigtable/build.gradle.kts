plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Bigtable tracker — classifies Cloud Bigtable operations (ReadRows/MutateRow/…) " +
    "and records them as tracked interactions (Bigtable category -> database shape). Pure logic + a " +
    "two-phase recorder; depends only on core (the Bigtable SDK hook feeds it)."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
