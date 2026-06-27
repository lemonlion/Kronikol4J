plugins {
    id("kronikol4j.java-library-conventions")
}

description = "Kronikol4J Hibernate/JPA tracker — the ORM integration point. A Hibernate " +
    "StatementInspector classifies each issued SQL statement (via the shared UnifiedSqlClassifier) and " +
    "records it as a tracked interaction. Builds on the JDBC module; Hibernate is compileOnly."

dependencies {
    api(project(":kronikol4j-jdbc"))
    // The Hibernate SPI the inspector implements (the user brings Hibernate).
    compileOnly("org.hibernate.orm:hibernate-core:6.5.2.Final")
    testImplementation(project(":kronikol4j-junit5"))
    testImplementation(project(":kronikol4j-diagram"))
    testImplementation("org.hibernate.orm:hibernate-core:6.5.2.Final")
}
