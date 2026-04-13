/*
 * Platform Security Module - Build Configuration
 *
 * Contains authentication, authorization, compliance, and security filters.
 * Shared between server and gateway for consistent auth enforcement.
 */

plugins {
    id("java-module")
    `java-library`
}

dependencies {
    // Minimal dependencies for security module
    // Should be lightweight to allow reuse

    implementation(project(":platform:java:security"))

    // ActiveJ for HTTP filters
    implementation(libs.activej.http)
    implementation(libs.activej.promise)

    // JWT handling - using Java JWT library
    // implementation(libs.auth0.jwt)  // TODO: Add to version catalog if needed

    // Jackson
    implementation(libs.jackson.databind)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 50 classes (keep security module lean)
