/**
 * resilience-patterns — K-18 Resilience for the app-platform kernel.
 *
 * Composes platform resilience primitives (CircuitBreaker, Bulkhead, RetryPolicy)
 * into named presets for the financial kernel services (ledger, IAM, calendar, secrets).
 *
 * Does NOT re-implement resilience algorithms — those live in platform:java:core.
 *
 * K-18 stories: K18-001 through K18-007.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-18 Resilience patterns: circuit breaker, bulkhead, retry for kernel services"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform (provides CircuitBreaker, Bulkhead, RetryPolicy, RetryContext) ─
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    // ─── ActiveJ async runtime ────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Logging ──────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)

    // ─── Test ─────────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
