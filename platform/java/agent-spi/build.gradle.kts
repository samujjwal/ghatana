plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "2026.3.1-SNAPSHOT"

description = "Agent SPI - Service Provider Interface for pluggable agent implementations"

dependencies {
    // Depends on agent-api for contract types
    api(project(":platform:java:agent-api"))

    // ActiveJ Promise for async registry contracts
    api(libs.activej.promise)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
