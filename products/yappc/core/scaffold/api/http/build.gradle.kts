/*
 * YAPPC HTTP API Module
 * RESTful and WebSocket API for YAPPC scaffold operations
 */

plugins {
    id("java-library")
}

description = "YAPPC HTTP API - RESTful and WebSocket access to scaffold operations"

dependencies {
    // Internal dependencies
    implementation(project(":products:yappc:core:scaffold:api"))
    implementation(project(":products:yappc:core:scaffold:core"))

    // ActiveJ HTTP (ADR-004 compliant)
    implementation(project(":platform:java:http"))
    implementation(libs.activej.http)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)

    // JSON serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("ch.qos.logback:logback-classic:1.4.11")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
