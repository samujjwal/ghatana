/**
 * integration-testing — Finance Platform Integration & Chaos Test Infrastructure (M4)
 *
 * Provides:
 *   - End-to-end scenario test runner over the live kernel+domain-pack stack
 *   - Chaos testing scenarios (network partitions, DB failovers, Kafka broker restarts)
 *   - Performance baseline capture and regression detection
 *   - GA readiness gate checklist execution
 *
 * Sprint 17-30 (M4 phase). Not deployed as a runtime artifact — test-only module.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Integration & chaos test infrastructure for GA readiness validation"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform testing utilities ───────────────────────────────────────────
    api(project(":platform:java:testing"))

    // ─── Kernel modules under test ────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:event-store"))
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:iam"))
    implementation(project(":products:app-platform:kernel:ledger-framework"))
    implementation(project(":products:app-platform:kernel:observability-sdk"))
    implementation(project(":products:app-platform:kernel:resilience-patterns"))

    // ─── Domain packs under test ──────────────────────────────────────────────
    implementation(project(":products:app-platform:domain-packs:oms"))
    implementation(project(":products:app-platform:domain-packs:risk-engine"))

    // ─── Security & Chaos ─────────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:secrets-management"))
    implementation(project(":products:app-platform:kernel:config-engine"))

    // ─── Test frameworks ─────────────────────────────────────────────────────
    implementation(libs.junit.jupiter)
    implementation(libs.assertj.core)
    implementation(libs.testcontainers.junit.jupiter)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.kafka)

    // ─── HTTP client (REST API integration tests) ─────────────────────────────
    implementation(libs.okhttp)

    // ─── ActiveJ ─────────────────────────────────────────────────────────────
    implementation(libs.activej.promise)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    runtimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Chaos tests require longer timeouts
    systemProperty("chaos.test.timeout.seconds", "120")
}
