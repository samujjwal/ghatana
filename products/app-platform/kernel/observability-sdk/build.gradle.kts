/**
 * observability-sdk — Finance kernel observability extension (K-06)
 *
 * Thin wrapper around platform:java:observability that adds:
 *   - Finance-specific metric name constants (K06-F01)
 *   - Ledger-specific Micrometer meters (journal throughput, balance updates, hash-chain errors)
 *   - Finance SLO definitions wrapping platform SloChecker (K06-F06)
 *   - Platform health check registrations for kernel services
 *
 * Reuse: ~100% from platform:java:observability + platform:java:observability-http.
 * No reimplementation — configure and extend only.
 *
 * Sprint 3 stories: STORY-K06-001, STORY-K06-003, STORY-K06-006, STORY-K06-013
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Finance kernel observability SDK: metric constants, SLOs, health checks"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform observability (100% reuse — wrap, don't reimplement) ─────
    api(project(":platform:java:observability"))

    // ─── Micrometer (transitive via platform:java:observability, explicit for IDE) ─
    api(libs.micrometer.core)
    // Prometheus scrape endpoint (K06-006)
    api(libs.micrometer.registry.prometheus)

    // ─── Logging ────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.micrometer.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
