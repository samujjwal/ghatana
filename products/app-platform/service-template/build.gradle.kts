/**
 * service-template — Reference implementation for a kernel-integrated ActiveJ service.
 *
 * TC-P0-011 through TC-P0-018: Demonstrates the standard service anatomy:
 *   - ActiveJ HTTP server with health and readiness checks
 *   - EventloopTestBase for async unit tests
 *   - Platform dependency wiring (core, observability, database)
 *   - Graceful start / stop lifecycle
 *
 * Teams copy this module as a starting point for new kernel services.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Service template — canonical ActiveJ HTTP kernel service skeleton"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // Platform (reuse first — Golden Rule §1)
    // -------------------------------------------------------------------------
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    // -------------------------------------------------------------------------
    // ActiveJ runtime
    // -------------------------------------------------------------------------
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)

    // -------------------------------------------------------------------------
    // Observability
    // -------------------------------------------------------------------------
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // -------------------------------------------------------------------------
    // Testing
    // -------------------------------------------------------------------------
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
