/*
 * YAPPC Backend WebSocket Module
 * Real-time communication handlers (collaboration, events, agent streams).
 */
plugins {
    id("java-library")
}

description = "YAPPC Backend - WebSocket handlers for real-time features"

dependencies {
    // Internal
    api(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:core"))

    // ActiveJ
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
