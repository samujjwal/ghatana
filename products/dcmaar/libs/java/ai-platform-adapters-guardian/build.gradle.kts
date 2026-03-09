plugins {
    id("java-library")
}

group = "com.ghatana.guardian"
version = "1.0.0-SNAPSHOT"

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Core agent and observability abstractions
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
