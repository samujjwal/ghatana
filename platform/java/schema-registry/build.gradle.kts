plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

description = "Platform Schema Registry — event schema storage, validation, and compatibility enforcement"

dependencies {
    // Data-Cloud SPI for EventLogStore (event-sourced schema persistence)
    api(project(":products:data-cloud:spi"))

    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.eventloop)

    // JSON Schema validation
    implementation(libs.networknt.validator)

    // Jackson for JSON/schema parsing
    implementation(libs.jackson.databind)

    // Logging
    implementation(libs.slf4j.api)

    // Annotations
    compileOnly(libs.jetbrains.annotations)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:data-cloud:platform")) // InMemoryEventLogStoreProvider
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
