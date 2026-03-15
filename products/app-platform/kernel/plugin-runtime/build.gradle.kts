/**
 * plugin-runtime — K-04 Plugin Runtime for T1/T2/T3 sandbox isolation.
 *
 * Provides:
 *   - Plugin manifest Ed25519 signature verification (K04-001)
 *   - SHA-256 checksum validation (K04-002)
 *   - T1 config-only plugin loader (K04-003)
 *   - T2 scripted-rule sandbox with resource accounting (K04-004/K04-006)
 *   - T3 network-capable plugin runtime stub (K04-005)
 *   - Capability declaration and enforcement (K04-007/K04-008)
 *   - Tier escalation prevention (K04-006)
 *
 * Design:
 *   - No external sandbox libraries — T2 sandbox uses SecurityManager-style
 *     boundary enforcement in-process (GraalVM Polyglot deferred to Sprint 16).
 *   - All I/O wrapped via Promise.ofBlocking for ActiveJ compatibility.
 *   - Reuses K-07 audit trail and K-14 signing keys — no duplicate capabilities.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-04 Plugin runtime: manifest verification, T1/T2/T3 sandbox, capability enforcement"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform ─────────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:security"))
    api(project(":platform:java:observability"))

    // ─── Sibling kernel (reuse K-07 audit, K-14 secrets) ──────────────────────
    api(project(":products:app-platform:kernel:audit-trail"))
    api(project(":products:app-platform:kernel:secrets-management"))
    api(project(":products:app-platform:kernel:config-engine"))

    // ─── ActiveJ ──────────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.inject)

    // ─── JSON ─────────────────────────────────────────────────────────────────
    implementation(libs.jackson.databind)

    // ─── Metrics ──────────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)

    // ─── Logging ──────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)

    // ─── Testing ──────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
