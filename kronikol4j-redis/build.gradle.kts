plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Redis tracker — records cache commands as tracked interactions (Redis " +
    "category -> collections shape). A Lettuce/Jedis wrapper delegates to it. Depends only on core."

dependencies {
    api(project(":kronikol4j-core"))
    // The Lettuce sync command interface the wrapper proxies (the user brings Lettuce).
    compileOnly("io.lettuce:lettuce-core:6.3.2.RELEASE")
    // The Jedis command interface the Jedis wrapper proxies (the user brings Jedis).
    compileOnly("redis.clients:jedis:5.2.0")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
    testImplementation("redis.clients:jedis:5.2.0")
}
