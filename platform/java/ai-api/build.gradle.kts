plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

description = "AI API - Stable shared interfaces and abstractions for AI integration"

dependencies {
    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.eventloop)

    // Core Platform
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    // Logging
    api(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
