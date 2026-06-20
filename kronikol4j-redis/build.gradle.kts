plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Redis tracker — records cache commands as tracked interactions (Redis " +
    "category -> collections shape). A Lettuce/Jedis wrapper delegates to it. Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
}
